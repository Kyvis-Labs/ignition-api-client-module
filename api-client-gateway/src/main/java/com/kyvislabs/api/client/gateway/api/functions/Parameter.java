package com.kyvislabs.api.client.gateway.api.functions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Parameter implements YamlParser {
    private API api;
    private String name;
    private Object value;

    public Parameter(API api) {
        this.api = api;
    }

    public static List<Parameter> parseParameters(API api, Integer version, Map yamlMap) throws APIException {
        List<Parameter> parameters = Collections.synchronizedList(new ArrayList<>());
        if (yamlMap.containsKey("params")) {
            List paramsList = (List) yamlMap.get("params");
            for (Object paramsObj : paramsList) {
                Map paramsMap = (Map) paramsObj;
                Parameter parameter = new Parameter(api);
                parameter.parse(version, paramsMap);
                parameters.add(parameter);
            }
        }
        return parameters;
    }

    public static List<net.dongliu.requests.Parameter<Object>> getParameters(List<Parameter> parameters, VariableStore store) throws APIException {
        List<net.dongliu.requests.Parameter<Object>> params = new ArrayList<>();
        for (Parameter parameter : parameters) {
            params.add(net.dongliu.requests.Parameter.of(parameter.getName(), parameter.getValue(store)));
        }
        return params;
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        if (!yamlMap.containsKey("name") || !yamlMap.containsKey("value")) {
            throw new APIException("Parameter missing name/value pair: " + yamlMap.toString());
        }

        this.name = yamlMap.get("name").toString();

        Object value = yamlMap.get("value");
        if (value instanceof String) {
            this.value = ValueString.parseValueString(api, yamlMap, "value", true);
        } else {
            this.value = value;
        }
    }

    public synchronized String getName() {
        return name;
    }

    private synchronized Object getValue() {
        return value;
    }

    public Object getValue(VariableStore store) throws APIException {
        try {
            if (value instanceof ValueString) {
                return ((ValueString) getValue()).getValue(store);
            } else {
                return getValue();
            }
        } catch (Throwable ex) {
            throw new APIException("Error getting parameter value for '" + getName() + "'", ex);
        }
    }
}
