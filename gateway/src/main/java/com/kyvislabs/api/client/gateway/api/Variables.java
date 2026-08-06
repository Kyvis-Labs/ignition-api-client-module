package com.kyvislabs.api.client.gateway.api;

import com.inductiveautomation.ignition.gateway.secrets.Plaintext;
import com.inductiveautomation.ignition.gateway.secrets.Secret;
import com.inductiveautomation.ignition.gateway.secrets.SecretConfig;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;
import com.kyvislabs.api.client.gateway.records.APIResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Variables implements YamlParser, VariableStore {
    private Logger logger;
    private API api;
    private Set<String> configurationVariables;
    // In-memory variable store: key → plaintext value. A HashMap (not ConcurrentHashMap) because a
    // declared-but-unset variable is legitimately null, and ConcurrentHashMap.put() throws NPE on a
    // null value.
    private Map<String, String> variableValues;
    // Variable metadata: key → metadata
    private Map<String, VariableMeta> variableMeta;
    // Keys actually changed since the last persist() - see persist() for why this matters.
    private final Set<String> dirtyKeys = ConcurrentHashMap.newKeySet();

    public Variables(API api) {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Variables", api.getName()));
        this.api = api;
        this.configurationVariables = ConcurrentHashMap.newKeySet();
        this.variableValues = Collections.synchronizedMap(new HashMap<>());
        this.variableMeta = new ConcurrentHashMap<>();

        // Load variables from embedded APIResource
        APIResource resource = api.getResource();
        // ---- TEMPORARY MIGRATION - safe to delete once every gateway has been upgraded past the
        // point where all variables were encrypted regardless of sensitivity (see persist()) ----
        // Gateways upgraded from before the sensitive/plain-text field split have every non-sensitive
        // variable stuck encrypted in `value` with nothing in `plainValue`. Reading already falls back
        // to decrypting `value` below, so nothing is broken - but without this, a variable nobody ever
        // edits again would stay encrypted in config.json forever, since persist() only migrates a
        // variable's storage the next time it's actually changed. This collects any such variable so
        // it can be force-migrated after the load loop below, instead of waiting indefinitely.
        Set<String> legacyEncryptedNonSensitive = new HashSet<>();
        if (resource.variables() != null) {
            for (APIResource.APIVariable variable : resource.variables()) {
                logger.debug("Loading variable '" + variable.key() + "' from APIResource");
                String value = null;
                if (variable.sensitive()) {
                    // Sensitive variables always come from the encrypted field, regardless of
                    // whether plainValue happens to be populated (it shouldn't be, but this way a
                    // secret can never be read back from the wrong, unencrypted field).
                    if (variable.value() != null) {
                        try (Plaintext plaintext = Secret.create(api.getGatewayContext(), variable.value()).getPlaintext()) {
                            value = plaintext.getAsString();
                        } catch (Throwable t) {
                            logger.warn("Could not decrypt variable '" + variable.key() + "': " + t.getMessage());
                        }
                    }
                } else if (variable.plainValue() != null) {
                    value = variable.plainValue();
                } else if (variable.value() != null) {
                    // Legacy: resources persisted before plainValue existed have every variable
                    // encrypted regardless of sensitivity. Decrypt so this non-sensitive variable
                    // still reads correctly, and flag it for migration to plain-text storage below.
                    try (Plaintext plaintext = Secret.create(api.getGatewayContext(), variable.value()).getPlaintext()) {
                        value = plaintext.getAsString();
                    } catch (Throwable t) {
                        logger.warn("Could not decrypt variable '" + variable.key() + "': " + t.getMessage());
                    }
                    legacyEncryptedNonSensitive.add(variable.key());
                }
                variableValues.put(variable.key(), value);
                variableMeta.put(variable.key(), new VariableMeta(
                        variable.required(), variable.sensitive(), variable.hidden()
                ));
            }
        }
        if (!legacyEncryptedNonSensitive.isEmpty()) {
            logger.debug("Migrating " + legacyEncryptedNonSensitive.size() + " variable(s) from encrypted to plain-text storage: " + legacyEncryptedNonSensitive);
            dirtyKeys.addAll(legacyEncryptedNonSensitive);
            // Off this thread and slightly deferred rather than persisting synchronously here - this
            // constructor can run from inside the async resource-change callback that's reloading the
            // API, and persist() -> updateResource() blocks waiting on that same notification
            // machinery to confirm the push, which is exactly the kind of self-call this needs to
            // avoid. Each affected API migrates at most once: the next load finds nothing left to fix.
            api.getGatewayContext().getExecutionManager().executeOnce(this::persist);
        }
        // ---- END TEMPORARY MIGRATION ----
    }

    // Guards batchUpdate() below. Each individual setVariable(String,Object)/clearVariable() call
    // persists and triggers a full async reload of the owning API (see class-level docs) - chaining
    // several of them together (e.g. OAuth2 setting a fresh auth code and clearing three stale token
    // variables) left a window where a reload triggered by an early call could swap in a brand new
    // API/Variables instance whose own functions start running concurrently with this object's
    // remaining calls, racing to persist two different views of the same variables. batchUpdate()
    // suppresses the per-call persist and does exactly one at the end, closing that window.
    private final Object batchLock = new Object();
    private int batchDepth = 0;

    public void batchUpdate(Runnable changes) {
        synchronized (batchLock) {
            batchDepth++;
            try {
                changes.run();
            } finally {
                batchDepth--;
                if (batchDepth == 0) {
                    persist();
                }
            }
        }
    }

    private void persistIfNotBatching() {
        synchronized (batchLock) {
            if (batchDepth == 0) {
                persist();
            }
        }
    }

    public void clearVariable(String name) {
        synchronized (variableValues) {
            // Skip if already unset - avoids a pointless persist/reload every time something calls
            // clearVariable() on state that's already cleared (e.g. OAuth2's needsAuth() retrying
            // against an already-failed auth), which was needlessly widening the race window below.
            if (!variableValues.containsKey(name) || variableValues.get(name) == null) {
                return;
            }
            logger.debug("Clearing variable '" + name + "'");
            variableValues.put(name, null);
            dirtyKeys.add(name);
        }
        persistIfNotBatching();
    }

    public void setVariable(String name, Boolean required, Boolean hidden, Boolean sensitive) {
        setVariable(name, required, hidden, sensitive, null);
    }

    public void setVariable(String name, Boolean required, Boolean hidden, Boolean sensitive, String value) {
        synchronized (variableValues) {
            boolean changed;
            if (!variableValues.containsKey(name)) {
                logger.debug("Found new variable '" + name + "' with [value=" + value + ", required=" + required + ", hidden=" + hidden + ", sensitive=" + sensitive + "]");
                variableValues.put(name, value);
                variableMeta.put(name, new VariableMeta(
                        required != null && required,
                        sensitive != null && sensitive,
                        hidden != null && hidden
                ));
                changed = value != null;
            } else {
                logger.debug("Updating variable '" + name + "' with [value=" + value + ", required=" + required + ", hidden=" + hidden + ", sensitive=" + sensitive + "]");
                changed = value != null && !value.equals(variableValues.get(name));
                if (value != null) {
                    variableValues.put(name, value);
                }
                VariableMeta existing = variableMeta.getOrDefault(name, new VariableMeta(false, false, false));
                variableMeta.put(name, new VariableMeta(
                        required != null ? required : existing.required(),
                        sensitive != null ? sensitive : existing.sensitive(),
                        hidden != null ? hidden : existing.hidden()
                ));
            }
            if (changed) {
                dirtyKeys.add(name);
            }
            configurationVariables.add(name);
        }
    }

    @Override
    public synchronized String getStoreName() {
        return "variables";
    }

    @Override
    public synchronized String getVariable(String name) throws APIException {
        if (variableValues.containsKey(name)) {
            return variableValues.get(name);
        }
        throw new APIException("Variable '" + name + "' doesn't exist");
    }

    @Override
    public void setVariable(String name, Object value) {
        setVariable(name, null, null, null, value == null ? null : value.toString());
        // This overload is the runtime entry point (auth flows, OAuth2 callback), unlike the
        // config-driven overloads used by parse()/initializeVariables() - persist here so the
        // change (e.g. a refreshed token) survives a gateway restart. Suppressed while inside
        // batchUpdate() so a multi-variable change persists once, atomically.
        persistIfNotBatching();
    }

    public void parse(Integer version, Map yamlMap) {
        if (yamlMap.containsKey("variables")) {
            Map variablesMap = (Map) yamlMap.get("variables");
            Iterator<String> it = variablesMap.keySet().iterator();
            while (it.hasNext()) {
                String name = it.next();
                Map variableMap = (Map) variablesMap.get(name);

                boolean required = (boolean) variableMap.getOrDefault("required", true);
                boolean sensitive = (boolean) variableMap.getOrDefault("sensitive", false);
                boolean hidden = (boolean) variableMap.getOrDefault("hidden", false);
                boolean uuid = (boolean) variableMap.getOrDefault("uuid", false);

                // Only seed a default/uuid the first time this variable is ever declared - this
                // method runs on every parse/reload (every persisted variable change reloads the
                // whole API - see class docs), so re-evaluating "default"/uuid unconditionally here
                // clobbered whatever value the user had actually saved (via the Variables drawer,
                // OAuth2, etc.) on every single reload, making edits appear to silently revert.
                String value = null;
                if (!hasValue(name)) {
                    if (uuid) {
                        value = UUID.randomUUID().toString();
                    } else if (variableMap.containsKey("default")) {
                        value = variableMap.get("default").toString();
                    }
                }

                setVariable(name, required, hidden, sensitive, value);
            }
        }
    }

    private boolean hasValue(String name) {
        synchronized (variableValues) {
            return variableValues.containsKey(name) && variableValues.get(name) != null;
        }
    }

    /**
     * Persists changed variable values back into the API's ConfigurationManager resource so they
     * survive a gateway restart. Only config-declared and "auth-" prefixed variables are persisted -
     * matching the old (8.1) cleanup behavior in initComplete() - anything else stays in-memory only
     * for the lifetime of this API instance.
     *
     * Merges just the dirty keys into whatever's CURRENTLY persisted, rather than overwriting the
     * whole variables list from this instance's own in-memory snapshot. This module reloads the
     * whole API - constructing a brand new Variables instance - on every persisted variable change,
     * so more than one Variables instance for the same API can legitimately be alive and persisting
     * around the same time (e.g. a background OAuth2 token refresh racing a user's edit in the
     * Variables drawer). A wholesale replace here would let whichever instance's persist() call
     * lands last silently wipe out every other variable that instance didn't itself know about,
     * even ones it never touched - which is exactly what made edited variables appear to silently
     * revert. Merging by key means two instances changing different variables can't clobber each
     * other; only genuine same-key races are still last-write-wins, which is unavoidable.
     */
    private void persist() {
        Map<String, String> dirtySnapshot = new HashMap<>();
        Map<String, VariableMeta> dirtyMeta = new HashMap<>();
        Set<String> keysToWrite;
        synchronized (variableValues) {
            if (dirtyKeys.isEmpty()) {
                return;
            }
            keysToWrite = new HashSet<>(dirtyKeys);
            for (String key : keysToWrite) {
                dirtySnapshot.put(key, variableValues.get(key));
                dirtyMeta.put(key, variableMeta.getOrDefault(key, new VariableMeta(false, false, false)));
            }
            dirtyKeys.removeAll(keysToWrite);
        }

        api.persistResource(current -> {
            Map<String, APIResource.APIVariable> merged = new LinkedHashMap<>();
            if (current.variables() != null) {
                for (APIResource.APIVariable v : current.variables()) {
                    merged.put(v.key(), v);
                }
            }

            for (String key : keysToWrite) {
                if (!configurationVariables.contains(key) && !key.startsWith("auth-")) {
                    continue;
                }
                VariableMeta meta = dirtyMeta.get(key);
                String value = dirtySnapshot.get(key);
                // A null value means "never set"/"cleared" and must stay null through the round-trip
                // - encoding it as an empty-string secret here would turn "unset" into a permanent
                // "" after the next reload (variable.value() != null in the constructor above),
                // which broke code like OAuth2.hasExpired() that only special-cases null, not "".
                // Only sensitive variables go through encryption (value/SecretConfig) - everything
                // else is stored as plain text (plainValue) so the config isn't opaque ciphertext for
                // ordinary, non-secret settings.
                merged.put(key, new APIResource.APIVariable(
                        key,
                        meta.sensitive() && value != null ? toSecretConfig(value) : null,
                        !meta.sensitive() ? value : null,
                        meta.required(),
                        meta.sensitive(),
                        meta.hidden()
                ));
            }

            return new APIResource(current.enabled(), current.configuration(), new ArrayList<>(merged.values()), current.certificate(), current.webhookKeys());
        });
    }

    private SecretConfig toSecretConfig(String plaintextValue) {
        try (Plaintext plaintext = Plaintext.fromString(plaintextValue != null ? plaintextValue : "")) {
            return SecretConfig.embedded(api.getGatewayContext().getSystemEncryptionService().encryptToJson(plaintext));
        } catch (Throwable t) {
            logger.error("Error encrypting variable value for persistence", t);
            return null;
        }
    }

    /**
     * value is only populated for non-sensitive variables - sensitive ones never cross the REST
     * boundary as plaintext, only hasValue (matches the "change password" masking UX used elsewhere).
     */
    public record VariableInfo(String key, boolean required, boolean sensitive, boolean hidden, boolean hasValue, String value) {}

    /**
     * Snapshot of all known variables (config-declared, auth-, and any other runtime ones) for
     * display in the Variables drawer. Split into editable ("required && !hidden", matching the old
     * 8.1 Wicket panel's user-facing section) vs read-only by the caller.
     */
    public synchronized List<VariableInfo> getAllVariables() {
        List<VariableInfo> result = new ArrayList<>();
        synchronized (variableValues) {
            for (String key : variableValues.keySet()) {
                VariableMeta meta = variableMeta.getOrDefault(key, new VariableMeta(false, false, false));
                String value = variableValues.get(key);
                result.add(new VariableInfo(key, meta.required(), meta.sensitive(), meta.hidden(), value != null, meta.sensitive() ? null : value));
            }
        }
        return result;
    }

    public boolean initComplete() {
        boolean valid = true;
        synchronized (variableValues) {
            for (Map.Entry<String, String> entry : variableValues.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (configurationVariables.contains(key)) {
                    VariableMeta meta = variableMeta.get(key);
                    if (meta != null && meta.required() && value == null) {
                        valid = false;
                    }
                }
                // Non-config, non-auth variables are simply kept in memory (no DB delete needed)
            }
        }
        return valid;
    }

    private record VariableMeta(boolean required, boolean sensitive, boolean hidden) {}
}
