# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an **Ignition Module** that enables REST API interaction through YAML configuration without scripting. It targets Ignition 8.1+ and is built using the Ignition SDK (v8.1.10). The module is installed as a `.modl` file into an Ignition gateway.

## Build Commands

```bash
# Build the module (produces .modl file)
mvn clean package

# Output location
api-client-build/target/api-client-<version>.modl
```

There are no automated tests in this repository. Testing is done manually via the Ignition gateway UI or by installing the module.

## Module Architecture

This is a **Maven multi-module project** with scope-separated components that align with Ignition's deployment model:

| Module | Ignition Scope | Purpose |
|--------|---------------|---------|
| `api-client-common` | CDG (Client/Designer/Gateway) | Shared scripting interface and exceptions |
| `api-client-gateway` | G (Gateway only) | Core implementation — HTTP execution, YAML parsing, tag management |
| `api-client-client` | C (Vision Client) | RPC proxy for `system.api` scripting functions |
| `api-client-designer` | D (Designer) | Registers `system.api` in Designer scope |
| `api-client-build` | — | Packages all modules into the `.modl` file |

## Gateway Module Deep Dive

The `api-client-gateway` module contains nearly all logic. Key subsystems:

**Lifecycle & Entry Point**
- `GatewayHook.java` — Module lifecycle (startup/shutdown), registers tag providers, servlets, and UI pages
- `managers/APIManager.java` — Singleton managing all API instances; listens for DB record changes via `IRecordListener`

**API Execution Pipeline**
1. API config is stored as YAML in `database/APIRecord.java` (persisted in Ignition's internal DB)
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
- `VariableAction` — stores values in module variables
- `WebhookAction` — triggers outbound webhooks
- `StoreFileAction` — saves response content to files
- `RunIf` / `Switch` / `Case` — conditional logic

**Tag Management**
`managers/TagManager.java` and `managers/TagBuilder.java` create and manage a real-time tag provider named `"API"` in Ignition.

**Database Records** (Ignition's internal persistent store)
- `APIRecord` — stores the full YAML configuration
- `APIVariableRecord` — stores encrypted variable values
- `APIWebhookRecord`, `APIFileRecord`, `APICertificateRecord` — supporting metadata

## Scripting Interface

The module exposes `system.api` to Ignition scripting environments:

```python
system.api.invokeFunction("apiName", "functionName", {"param": value})
```

The common interface is defined in `api-client-common`, implemented in gateway (`ScriptFunctionsScriptModule.java`), and proxied via RPC in client and designer scopes.

## Bundled Library

The HTTP client library `net.dongliu.requests` is included as source in the gateway module (not a Maven dependency) because Ignition modules must bundle all non-SDK dependencies.

## Key Dependencies

- **snakeyaml** — YAML parsing of API configurations
- **json-path** — JSONPath expressions in response handling
- **jsoup** — HTML/XML parsing
- Optional JSON libraries (jackson, gson, fastjson) are detected at runtime

## Ignition SDK Notes

- All Ignition SDK dependencies use `provided` scope — they are supplied by the Ignition runtime
- The `ignition-maven-plugin` handles `.modl` packaging
- The module is scoped using standard Ignition module scopes (C/D/G)
