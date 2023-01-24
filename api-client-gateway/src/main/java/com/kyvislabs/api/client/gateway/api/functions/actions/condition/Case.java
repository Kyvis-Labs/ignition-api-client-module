package com.kyvislabs.api.client.gateway.api.functions.actions.condition;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Case implements YamlParser {
    private Logger logger;
    private Function function;
    private ValueString conditionKey;
    private ConditionOperator conditionOperator;
    private ValueString conditionValue;
    private Map<String, ValueString> variables;

    public Case(Logger logger, Function function) {
        this.logger = logger;
        this.function = function;
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        if (!yamlMap.containsKey("conditionKey")) {
            throw new APIException("Missing condition key");
        }

        if (!yamlMap.containsKey("conditionOperator")) {
            throw new APIException("Missing condition operator");
        }

        if (!yamlMap.containsKey("conditionValue")) {
            throw new APIException("Missing condition value");
        }

        this.conditionKey = ValueString.parseValueString(function.getApi(), yamlMap, "conditionKey", true);
        this.conditionOperator = ConditionOperator.valueOf(yamlMap.getOrDefault("conditionOperator", "EQ").toString().toUpperCase());
        this.conditionValue = ValueString.parseValueString(function.getApi(), yamlMap, "conditionValue", true);
        this.variables = parseVariables(yamlMap, function);
    }

    public static Map<String, ValueString> parseVariables(Map yamlMap, Function function) throws APIException {
        Map<String, ValueString> variables = new ConcurrentHashMap<>();
        if (yamlMap.containsKey("variables")) {
            List variablesList = (List) yamlMap.get("variables");
            for (Object variableObj : variablesList) {
                Map variableMap = (Map) variableObj;

                if (!variableMap.containsKey("name")) {
                    throw new APIException("Variable missing name");
                }

                if (!variableMap.containsKey("value")) {
                    throw new APIException("Variable missing value");
                }

                String name = (String) variableMap.get("name");
                ValueString value = ValueString.parseValueString(function.getApi(), variableMap, "value", true);
                variables.put(name, value);
            }
        }
        return variables;
    }

    public synchronized ValueString getConditionKey() {
        return conditionKey;
    }

    public synchronized ConditionOperator getConditionOperator() {
        return conditionOperator;
    }

    public synchronized ValueString getConditionValue() {
        return conditionValue;
    }

    public synchronized Map<String, ValueString> getVariables() {
        return variables;
    }

    public synchronized ValueString getVariable(String name) throws APIException {
        if (!variables.containsKey(name)) {
            throw new APIException("Variable '" + name + "' doesn't exist");
        }

        return variables.get(name);
    }

    public boolean matches(VariableStore store, String response) throws APIException {
        return matches(store, response, null);
    }

    public boolean matches(VariableStore store, String response, String item) throws APIException {
        String conditionKey = getConditionKey().getValue(store, response, item);

        logger.debug("Checking case [conditionKey=" + conditionKey + ", conditionOperator=" + getConditionOperator().toString() + ", response=" + response + "]");

        if (getConditionOperator().equals(Case.ConditionOperator.IN)) {
            List<String> conditionValues = getConditionValue().getValues(store, response, item);
            logger.debug("Looking into values " + conditionValues.stream().collect(Collectors.joining(", ", "{", "}")));

            if (conditionValues.contains(conditionKey)) {
                logger.debug("Condition key '" + conditionKey + "' found");
                return true;
            }
        } else {
            String conditionValue = getConditionValue().getValue(store, response, item);
            logger.debug("Checking condition [value=" + conditionValue + "]");

            if (getConditionOperator().equals(Case.ConditionOperator.EQ)) {
                return conditionKey.equals(conditionValue);
            } else if (getConditionOperator().equals(Case.ConditionOperator.NEQ)) {
                return !conditionKey.equals(conditionValue);
            } else if (getConditionOperator().equals(Case.ConditionOperator.LT)) {
                return Double.valueOf(conditionKey) < Double.valueOf(conditionValue);
            } else if (getConditionOperator().equals(Case.ConditionOperator.LTE)) {
                return Double.valueOf(conditionKey) <= Double.valueOf(conditionValue);
            } else if (getConditionOperator().equals(Case.ConditionOperator.GT)) {
                return Double.valueOf(conditionKey) > Double.valueOf(conditionValue);
            } else if (getConditionOperator().equals(Case.ConditionOperator.GTE)) {
                return Double.valueOf(conditionKey) >= Double.valueOf(conditionValue);
            }
        }

        return false;
    }

    public enum ConditionOperator {
        EQ,
        LT,
        GT,
        LTE,
        GTE,
        IN,
        NEQ
    }
}
