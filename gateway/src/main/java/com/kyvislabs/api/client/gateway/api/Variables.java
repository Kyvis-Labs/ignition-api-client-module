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
    private List<String> configurationVariables;
    // In-memory variable store: key → plaintext value. A HashMap (not ConcurrentHashMap) because a
    // declared-but-unset variable is legitimately null, and ConcurrentHashMap.put() throws NPE on a
    // null value.
    private Map<String, String> variableValues;
    // Variable metadata: key → metadata
    private Map<String, VariableMeta> variableMeta;

    public Variables(API api) {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Variables", api.getName()));
        this.api = api;
        this.configurationVariables = Collections.synchronizedList(new ArrayList<>());
        this.variableValues = Collections.synchronizedMap(new HashMap<>());
        this.variableMeta = new ConcurrentHashMap<>();

        // Load variables from embedded APIResource
        APIResource resource = api.getResource();
        if (resource.variables() != null) {
            for (APIResource.APIVariable variable : resource.variables()) {
                logger.debug("Loading variable '" + variable.key() + "' from APIResource");
                String value = null;
                if (variable.value() != null) {
                    try (Plaintext plaintext = Secret.create(api.getGatewayContext(), variable.value()).getPlaintext()) {
                        value = plaintext.getAsString();
                    } catch (Throwable t) {
                        logger.warn("Could not decrypt variable '" + variable.key() + "': " + t.getMessage());
                    }
                }
                variableValues.put(variable.key(), value);
                variableMeta.put(variable.key(), new VariableMeta(
                        variable.required(), variable.sensitive(), variable.hidden()
                ));
            }
        }
    }

    public void clearVariable(String name) {
        if (variableValues.containsKey(name)) {
            logger.debug("Clearing variable '" + name + "'");
            variableValues.put(name, null);
            persist();
        }
    }

    public void setVariable(String name, Boolean required, Boolean hidden, Boolean sensitive) {
        setVariable(name, required, hidden, sensitive, null);
    }

    public void setVariable(String name, Boolean required, Boolean hidden, Boolean sensitive, String value) {
        if (!variableValues.containsKey(name)) {
            logger.debug("Found new variable '" + name + "' with [value=" + value + ", required=" + required + ", hidden=" + hidden + ", sensitive=" + sensitive + "]");
            variableValues.put(name, value);
            variableMeta.put(name, new VariableMeta(
                    required != null && required,
                    sensitive != null && sensitive,
                    hidden != null && hidden
            ));
        } else {
            logger.debug("Updating variable '" + name + "' with [value=" + value + ", required=" + required + ", hidden=" + hidden + ", sensitive=" + sensitive + "]");
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
        configurationVariables.add(name);
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
        // change (e.g. a refreshed token) survives a gateway restart.
        persist();
    }

    public void parse(Integer version, Map yamlMap) {
        if (yamlMap.containsKey("variables")) {
            Map variablesMap = (Map) yamlMap.get("variables");
            Iterator<String> it = variablesMap.keySet().iterator();
            while (it.hasNext()) {
                String name = it.next();
                Map variableMap = (Map) variablesMap.get(name);

                String value = null;
                boolean required = (boolean) variableMap.getOrDefault("required", true);
                boolean sensitive = (boolean) variableMap.getOrDefault("sensitive", false);
                boolean hidden = (boolean) variableMap.getOrDefault("hidden", false);
                boolean uuid = (boolean) variableMap.getOrDefault("uuid", false);

                if (uuid) {
                    value = UUID.randomUUID().toString();
                } else if (variableMap.containsKey("default")) {
                    value = variableMap.get("default").toString();
                }

                setVariable(name, required, hidden, sensitive, value);
            }
        }
    }

    /**
     * Persists the current variable values back into the API's ConfigurationManager resource so they
     * survive a gateway restart. Only config-declared and "auth-" prefixed variables are persisted -
     * matching the old (8.1) cleanup behavior in initComplete() - anything else stays in-memory only
     * for the lifetime of this API instance.
     */
    private void persist() {
        List<APIResource.APIVariable> persistedVariables = new ArrayList<>();
        synchronized (variableValues) {
            for (Map.Entry<String, String> entry : variableValues.entrySet()) {
                String key = entry.getKey();
                if (!configurationVariables.contains(key) && !key.startsWith("auth-")) {
                    continue;
                }
                VariableMeta meta = variableMeta.getOrDefault(key, new VariableMeta(false, false, false));
                persistedVariables.add(new APIResource.APIVariable(
                        key,
                        toSecretConfig(entry.getValue()),
                        meta.required(),
                        meta.sensitive(),
                        meta.hidden()
                ));
            }
        }

        api.persistResource(current -> new APIResource(
                current.enabled(), current.configuration(), persistedVariables, current.certificate(), current.webhookKeys()
        ));
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
