# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an **Ignition Module** that enables REST API interaction through YAML configuration without scripting. It targets **Ignition 8.3+** and is built using the Ignition SDK (v8.3.0). The module is installed as a `.modl` file into an Ignition gateway.

The module was migrated from Ignition 8.1 (Maven, PersistentRecord/Wicket UI) to 8.3 (Gradle, ConfigurationManager, React UI). When in doubt about "the old way" vs "the new way," trust what's actually in the tree — some 8.1 assumptions (numeric API ids, DB-backed persistence, Wicket config pages) no longer apply anywhere.

## Build Commands

```bash
# Build the module (produces .modl file)
./gradlew zipModule

# Output location
build/api-client.signed.modl   (or .unsigned.modl — module signing is skipped by default, see build.gradle.kts)
```

There are no automated tests in this repository. Testing is done manually via the Ignition gateway UI or by installing the module.

## Module Architecture

This is a **Gradle multi-module project** (Kotlin DSL) with scope-separated components that align with Ignition's deployment model:

| Module | Ignition Scope | Purpose |
|--------|---------------|---------|
| `common` | CDG (Client/Designer/Gateway) | Shared scripting interface, exceptions, and RPC serialization glue |
| `gateway` | G (Gateway only) | Core implementation — HTTP execution, YAML parsing, tag management, ConfigurationManager resources |
| `client` | C (Vision Client) | RPC proxy for `system.api` scripting functions |
| `designer` | D (Designer) | RPC proxy for `system.api` in Designer scope |
| `web-ui` | G | React/TypeScript config UI, built via webpack and bundled as a `SystemJsModule` |

The root `build.gradle.kts` uses the `io.ia.sdk.modl` Gradle plugin (not `ignition-maven-plugin`) to assemble and package the `.modl`. Module id is `com.kyvislabs.api.client`.

## Gateway Module Deep Dive

The `gateway` module contains nearly all logic. Key subsystems:

**Lifecycle & Entry Point**
- `GatewayHook.java` — Module lifecycle (startup/shutdown), registers the `APIResource` type with `ConfigurationManager`, registers the React UI's navigation entry (`SystemJsModule`), registers the RPC implementation (`getRpcImplementation()`), and lists migration strategies (`getRecordMigrationStrategies()`)
- `managers/APIManager.java` — Singleton (initialization-on-demand holder, not a null-check-and-assign) managing all API instances. Owns a `NamedResourceHandler<APIResource>` that reacts to ConfigurationManager add/update/remove events, and registers the module's raw `HttpServlet`s (`OAuth2Servlet`, `WebhookServlet`, `StoreFileServlet`) via `gatewayContext.getWebResourceManager().addServlet(...)` in `startup()` — these are **not** wired up anywhere else, so don't assume a different registration path exists.

**`API.shutdown()` vs `API.pause()`:** `shutdown()` is full teardown — stops functions/webhooks *and* unregisters this instance's health check/metrics (`unregisterMetrics()`) — used when the instance is being replaced by a reload or permanently removed. `pause()` only stops functions/webhooks, leaving metrics registered; use this when the `API` object itself stays alive and current (e.g. `OAuth2.needsAuth()` losing authorization mid-run) so the status the React UI reads keeps working. Calling `shutdown()` from a context where the instance isn't actually being replaced leaves its health check permanently unregistered with nothing to ever re-register it.

**Scheduled task self-cancellation:** `Schedule.shutdown()` and the equivalent cancellation points in `FunctionAction`/`Webhook`/`WebhookKey` all call `cancel(false)`, not `cancel(true)`, on their own `ScheduledFuture`s. These `shutdown()`/`schedule()` calls can happen from *inside* that same future's own currently-executing task (e.g. an OAuth2 auth failure inside a scheduled `FunctionExecutor` triggering `needsAuth()` → `api.pause()` → `Schedule.shutdown()` for the very function that's running) — `cancel(true)` would interrupt the calling thread itself mid-execution. Keep new cancellation sites consistent with this.

**Concurrent authentication:** `AuthType.authenticate()` is synchronized on a per-instance lock and re-checks `isAuthenticated()` once the lock is held, so two functions detecting "not authenticated" at the same time can't both kick off a token/code exchange concurrently — for an authorization-code grant the code is single-use, so a losing concurrent attempt would fail and its `needsAuth()` cleanup could wipe out the winning attempt's just-obtained token. Any new code that bypasses this wrapper to call an `AuthTypeInterface` implementation's `authenticate()` directly loses this protection.

**API Execution Pipeline**
1. API config is stored as YAML inside `records/APIResource.java`, a record persisted as a ConfigurationManager resource (module id `com.kyvislabs.api.client`, type id `api` — see `records/ResourceTypes.java`). APIs are looked up **by name**, not by a numeric id.
2. `api/API.java` parses YAML and coordinates authentication, functions, headers, variables, and webhooks
3. `api/functions/Function.java` defines individual HTTP calls
4. `api/functions/FunctionExecutor.java` performs the actual HTTP request (using the bundled `net.dongliu.requests` library)
5. Post-execution `api/functions/actions/` classes process responses (write tags, run scripts, call other functions, etc.)

**Value String Templating**
`api/valuestring/ValueString.java` processes template strings like `{variable:name}`, `{response:jsonPath}`, `{tag:tagPath}`. The `command/` and `function/` sub-packages handle specific token types.

**Authentication**
`api/authentication/` contains strategy implementations: `NoAuth`, `BasicAuth`, `Bearer`, `TokenAuth`, `SessionAuth`, `OAuth2`. Each implements `AuthTypeInterface`.

**Actions (Post-Response Processing)**
`api/functions/actions/` implements a chain of actions executed after a function response:
- `TagAction` — writes values to Ignition tags
- `ScriptAction` — executes Python scripts
- `FunctionAction` — calls other API functions
- `VariableAction` — stores values in the *function-local* variable store (`Function.setVariable`) — this is separate from, and does not persist to, the API-level `Variables` store below
- `WebhookAction` — triggers outbound webhooks. `Webhook.init()` (called on every API startup/reload) re-runs `execute()` on *every* known webhook key, not just ones created via `checkOnStart` — a key's `exists` flag and periodic TTL-recheck `ScheduledFuture` are transient, in-memory-only `WebhookKey` state, not part of the persisted `APIResource.APIWebhookKey`, so a key created dynamically via `WebhookAction` needs this unconditional re-verification pass to keep being monitored across a restart/reload
- `StoreFileAction` — saves response content to files under `<gatewayDataDir>/modules/com.kyvislabs.api.client/<apiName>/`; access is via a random token file (`<token>.token` containing the filename), not a DB row
- `RunIf` / `Switch` / `Case` — conditional logic

**Tag Management**
`managers/TagManager.java` and `managers/TagBuilder.java` create and manage a real-time tag provider named `"API"` in Ignition.

## Configuration Persistence (ConfigurationManager)

There is no PersistentRecord/DB table for API config anymore. Everything lives in the `APIResource` record (`records/APIResource.java`), embedded as one JSON blob per named API resource:
- `enabled`, `configuration` (the YAML string)
- `variables` — `List<APIVariable>`; each variable is either encrypted (`value`, a `SecretConfig`) or plain text (`plainValue`, a `String`) depending on `sensitive` — never both (see below)
- `certificate` — optional mTLS client cert/key
- `webhookKeys` — `List<APIWebhookKey>`, one entry per externally-issued webhook key, tagged with which named webhook it belongs to

**Important gotcha:** because variables and webhook keys live inside the *same* resource as the YAML config, persisting a runtime change (e.g. a refreshed OAuth2 token, or a new webhook key) goes through `API.persistResource(...)` → `APIManager.updateResource(...)` → `NamedResourceHandler.modify(...)`, which pushes a real resource change through ConfigurationManager. That **triggers a full async reload of that API instance** (`APIResourceHandler.onResourceUpdated` tears down and reconstructs it), the same as if someone edited the YAML by hand. `Variables.setVariable(String, Object)`/`clearVariable(...)` (the runtime entry points used by auth flows and the OAuth2 callback) persist; the config-parse-time overloads used by `parse()`/`initializeVariables()` (which run on *every* startup/reload) deliberately do **not**, to avoid a reload loop. Keep that split when touching this code — routing a startup-time call through the persisting path is an easy way to create an infinite reload loop.

Because a persisted variable change reloads the whole API, more than one `Variables` instance for the same API can legitimately be alive and persisting around the same time (e.g. a background OAuth2 token refresh racing a user's edit in the Variables drawer UI). `Variables.persist()` handles this by tracking which keys actually changed (`dirtyKeys`) and merging just those into whatever's *currently* persisted, rather than overwriting the whole `variables` list from one instance's own (possibly stale) in-memory snapshot — a wholesale replace let one instance silently wipe out changes an unrelated instance had already made. When more than one variable needs to change together as a single logical step (e.g. OAuth2 setting a fresh auth code and clearing three stale token variables), use `Variables.batchUpdate(Runnable)` instead of several individual `setVariable`/`clearVariable` calls — it persists once at the end instead of once per call, closing a window where a reload triggered by an early call could race the remaining calls against a freshly-reloaded instance. `APIManager.updateResource(...)` also holds a per-API-name lock and retries (waiting on the actual push result, not just the synchronous call) on a signature conflict, since `NamedResourceHandler.modify(...)` is not safe to call concurrently for the same resource.

`Variables.parse()` only seeds a `default`/`uuid` value the *first* time a variable is declared (i.e. when it has no value yet) — since `parse()` reruns on every reload, unconditionally reapplying `default`/`uuid` there would clobber whatever the user had actually saved (via the Variables drawer, OAuth2, etc.) on every single reload. `uuid: true` therefore generates a stable identifier once, not a fresh one per load.

Secrets (`APIVariable.value`, `APICertificate.privateKey`) are `com.inductiveautomation.ignition.gateway.secrets.SecretConfig`, and are used **only** for variables declared `sensitive: true` — everything else is stored as plain text in `APIVariable.plainValue` so ordinary, non-secret config isn't opaque ciphertext. To read plaintext: `Secret.create(gatewayContext, secretConfig).getPlaintext()` (try-with-resources, it's `Closeable`). To write: `SecretConfig.embedded(gatewayContext.getSystemEncryptionService().encryptToJson(Plaintext.fromString(value)))`. Reading is gated on `sensitive`, matching the write side: sensitive variables always decrypt `value`; non-sensitive ones read `plainValue` (falling back to decrypting `value` for resources persisted before this split, which encrypted everything).

**Temporary migration (`Variables`'s constructor, clearly marked `TEMPORARY MIGRATION` — delete once every gateway has been upgraded past 2.0.5):** self-healing on next write alone leaves a non-sensitive variable encrypted forever if nobody ever edits it again after upgrading. The constructor detects any non-sensitive variable still using the old encrypted-only format and schedules a one-time `persist()` (via `executionManager.executeOnce(...)`, deliberately off the constructing thread — this constructor can run from inside the async resource-change callback that's reloading the API, and `persist()` → `updateResource()` blocks on that same notification machinery to confirm the push, which self-calling would risk) to migrate it to `plainValue`. Self-limiting: each API migrates at most once, since the next load finds nothing left to fix.

**Migration from 8.1**: `records/APIMigrationStrategy.java` is a custom `IdbMigrationStrategy` (not the SDK's generic `NamedRecordMigrationStrategy`, because this module has child tables — variables/certificate/webhooks — that the generic strategy can't join). `records/legacy/Legacy*Record.java` are read-only mirrors of the deleted 8.1 `PersistentRecord` schema (`API`, `APIVariable`, `APICertificate`, `APIWebhook` tables), kept only so the migration strategy can query them via SimpleORM. Don't wire these into anything else.

## Web UI (React)

The `web-ui` module (`web-ui/src/pages/APIs/`) is a small React/TypeScript app, built with webpack and mounted into the gateway page shell as a `SystemJsModule` (see `GatewayHook.JS_MODULE`). It talks to the gateway over the **generic ConfigurationManager REST routes**, not a custom API:

- List: `GET /data/api/v1/resources/list/{moduleId}/{typeId}?limit=&offset=` → `{ items, metadata }`
- Find one: `GET /data/api/v1/resources/find/{moduleId}/{typeId}/{name}`
- Create/Update: `POST`/`PUT /data/api/v1/resources/{moduleId}/{typeId}` with an **array**-wrapped body — `[{ name, config }]` (create) or `[{ name, signature, config }]` (update)
- Delete: `DELETE /data/api/v1/resources/{moduleId}/{typeId}/{name}/{signature}`

`{moduleId}/{typeId}` must match `ResourceTypes.java` exactly (`com.kyvislabs.api.client/api`). The JSON envelope wraps the typed `APIResource` fields under a `config` key alongside resource-level `name`/`description`/`signature` — it is **not** a flat object. Mutating requests need an `X-CSRF-Token` header, sourced via `react-redux`'s `useSelector((state) => state.userSession.csrfToken)` (the gateway page shell provides the Redux store; `react-redux` is a webpack external, not bundled). When editing, always round-trip the *full* `config` object — a `PUT` replaces the whole resource, so sending back only the edited fields silently wipes `variables`/`certificate`/`webhookKeys`.

If you're building a similar page and unsure of the contract, check a working reference implementation rather than guessing the URL scheme — a wrong guess here fails silently as a generic fetch error with no server-side clue.

## Scripting Interface

The module exposes `system.api` to Ignition scripting environments:

```python
system.api.invokeFunction("apiName", "functionName", {"param": value})
```

The common interface (`common/.../scripting/interfaces/APIsInterface.java`) is implemented directly in gateway scope (`gateway/.../scripting/ScriptFunctionsScriptModule.java`, registered straight into the script manager — no RPC involved there). Client and Designer scopes instead get a proxy (`ClientAPIsScriptModule`) built from `GatewayConnection.getRpcInterface(...)`, calling across the RPC boundary via `GatewayHook.getRpcImplementation()` / `GatewayRpcImplementation`.

**Gotcha:** `invokeFunction`'s `functionParameters` argument is a Jython `PyDictionary`, which `ProtoRpcSerializer.DEFAULT_INSTANCE` cannot serialize on its own. `ClientAPIsScriptModule.SERIALIZER` must build a `ProtoRpcSerializer` with `ScriptFunctionsPyDictionaryProtoAdapter` registered via `.addBinaryAdapter(PyDictionary.class, ...)`, or RPC calls from Vision Client/Designer scope fail. If you add another RPC-crossing method with a non-primitive Jython/Java type, check whether it needs a similar adapter before assuming the default serializer handles it.

## Bundled Library

The HTTP client library `net.dongliu.requests` is included as source in the gateway module (not a Gradle dependency) because Ignition modules must bundle all non-SDK dependencies.

## Key Dependencies

- **snakeyaml** — YAML parsing of API configurations
- **json-path** — JSONPath expressions in response handling
- **jsoup** — HTML/XML parsing
- Optional JSON libraries (jackson, gson, fastjson) are detected at runtime

## Ignition SDK Notes

- All Ignition SDK dependencies use `compileOnly` scope — they are supplied by the Ignition runtime
- The `io.ia.sdk.modl` Gradle plugin handles `.modl` packaging (see root `build.gradle.kts`)
- The module is scoped using standard Ignition module scopes (C/D/G), declared in `ignitionModule.projectScopes` in the root `build.gradle.kts`
- `simple-orm` (SimpleORM) and the legacy `PersistentRecord` classes in `gateway/.../localdb/persistence` are still present in the 8.3 SDK jar specifically to support migration strategies (see `records/legacy/`) — they are not otherwise part of the active runtime path
