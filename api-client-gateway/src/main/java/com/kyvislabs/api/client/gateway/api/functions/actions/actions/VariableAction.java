package com.kyvislabs.api.client.gateway.api.functions.actions.actions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class VariableAction extends Action {
    public static final String ACTION = "variable";

    private Logger logger;
    private String name;
    private ValueString value;
    private ValueString tagPath;

    public VariableAction(Function function) {
        super(function);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Action.Variable", function.getApi().getName(), function.getLoggerName()));
    }

    public static List<VariableAction> parseVariables(Map yamlMap, Action action) throws APIException {
        List<VariableAction> variables = Collections.synchronizedList(new ArrayList<>());
        if (yamlMap.containsKey("variables")) {
            List variablesList = (List) yamlMap.get("variables");
            for (Object variableObj : variablesList) {
                Map variableMap = (Map) variableObj;
                VariableAction variable = new VariableAction(action.getFunction());
                variable.parse(variableMap);
                variables.add(variable);
            }
        }
        return variables;
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        super.parse(yamlMap);

        if (!yamlMap.containsKey("name")) {
            throw new APIException("Variable missing name");
        }

        if (!yamlMap.containsKey("value") && !yamlMap.containsKey("tagPath")) {
            throw new APIException("Variable missing value or tag path");
        }

        this.name = (String) yamlMap.get("name");
        this.value = ValueString.parseValueString(function.getApi(), yamlMap, "value");
        this.tagPath = ValueString.parseValueString(function.getApi(), yamlMap, "tagPath");
    }

    public synchronized String getName() {
        return name;
    }

    private synchronized ValueString getValue() {
        return value;
    }

    private synchronized ValueString getTagPath() {
        return tagPath;
    }

    @Override
    public void handleResponse(VariableStore store, int statusCode, String contentType, String response) throws APIException {
        Object value = getValue(store, response);
        logger.debug("Handling variable action storing [name=" + getName() + ", value=" + (value == null ? "null" : value.toString()) + "]");
        function.setVariable(getName(), value);
    }

    public synchronized Object getValue(VariableStore store) throws APIException {
        return getValue(store, null, null);
    }

    public synchronized Object getValue(VariableStore store, String response) throws APIException {
        return getValue(store, response, null);
    }

    public synchronized Object getValue(VariableStore store, String response, String item) throws APIException {
        Object retValue = null;
        try {
            if (getTagPath() != null) {
                String tp = getTagPath().getValue(store, response, item);
                retValue = function.getApi().getTagManager().readTag(tp).getValue();
            } else {
                retValue = getValue().getValue(store, response, item);
            }
        } catch (Throwable ex) {
            throw new APIException("Error getting value", ex);
        }
        return retValue;
    }
}
