package com.kyvislabs.api.client.gateway.api;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;
import com.kyvislabs.api.client.gateway.database.APIVariableRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Variables implements YamlParser, VariableStore {
    private Logger logger;
    private API api;
    private List<String> configurationVariables;
    private Map<String, APIVariableRecord> variables;

    public Variables(API api) {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Variables", api.getName()));
        this.api = api;
        this.configurationVariables = Collections.synchronizedList(new ArrayList<>());
        this.variables = new ConcurrentHashMap<>();

        SQuery<APIVariableRecord> query = new SQuery<>(APIVariableRecord.META);
        query.eq(APIVariableRecord.APIId, api.getId());
        List<APIVariableRecord> dbVariables = api.getGatewayContext().getPersistenceInterface().query(query);
        for (APIVariableRecord dbVariable : dbVariables) {
            logger.debug("Loading variable '" + dbVariable.getKey() + "' from the internal database");
            variables.put(dbVariable.getKey(), dbVariable);
        }
    }

    public void clearVariable(String name) {
        if (variables.containsKey(name)) {
            logger.debug("Clearing variable '" + name + "'");
            APIVariableRecord variable = variables.get(name);
            variable.setValue(null);
            api.getGatewayContext().getPersistenceInterface().save(variable);
        }
    }

    public void setVariable(String name, Boolean required, Boolean hidden, Boolean sensitive) {
        setVariable(name, required, hidden, sensitive, null);
    }

    public void setVariable(String name, Boolean required, Boolean hidden, Boolean sensitive, String value) {
        if (!variables.containsKey(name)) {
            logger.debug("Found new variable '" + name + "' with [value=" + value + ", required=" + required + ", hidden=" + hidden + ", sensitive=" + sensitive + "]");
            APIVariableRecord newVariable = api.getGatewayContext().getPersistenceInterface().createNew(APIVariableRecord.META);
            newVariable.setAPIId(api.getId());
            newVariable.setKey(name);
            newVariable.setRequired(required == null ? false : required);
            newVariable.setHidden(hidden == null ? false : hidden);
            newVariable.setSensitive(sensitive == null ? false : sensitive);
            newVariable.setValue(value);
            api.getGatewayContext().getPersistenceInterface().save(newVariable);
            variables.put(name, newVariable);
        } else {
            logger.debug("Updating variable '" + name + "' with [value=" + value + ", required=" + required + ", hidden=" + hidden + ", sensitive=" + sensitive + "]");
            APIVariableRecord variable = variables.get(name);
            if (value != null) {
                variable.setValue(value);
            }
            if (required != null) {
                variable.setRequired(required);
            }
            if (sensitive != null) {
                variable.setSensitive(sensitive);
            }
            if (hidden != null) {
                variable.setHidden(hidden);
            }
            api.getGatewayContext().getPersistenceInterface().save(variable);
        }

        configurationVariables.add(name);
    }

    @Override
    public synchronized String getStoreName() {
        return "variables";
    }

    @Override
    public synchronized String getVariable(String name) throws APIException {
        if (variables.containsKey(name)) {
            return variables.get(name).getValue();
        }

        throw new APIException("Variable '" + name + "' doesn't exist");
    }

    @Override
    public void setVariable(String name, Object value) {
        setVariable(name, null, null, null, value == null ? null : value.toString());
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

    public boolean initComplete() {
        boolean valid = true;

        for (APIVariableRecord variable : variables.values()) {
            if (configurationVariables.contains(variable.getKey())) {
                if (variable.isRequired() && variable.getValue() == null) {
                    valid = false;
                }
            } else if (!variable.getKey().startsWith("auth-")) {
                variable.deleteRecord();
                api.getGatewayContext().getPersistenceInterface().save(variable);
            }
        }
        return valid;
    }
}
