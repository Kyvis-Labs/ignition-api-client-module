package com.kyvislabs.api.client.gateway.api.interfaces;

import com.kyvislabs.api.client.common.exceptions.APIException;

import java.util.List;

public abstract class ValueHandler {
    public List<String> getValues() throws APIException {
        return getValues(null, null, null);
    }

    public List<String> getValues(VariableStore store) throws APIException {
        return getValues(store, null, null);
    }

    public List<String> getValues(VariableStore store, String response) throws APIException {
        return getValues(store, response, null);
    }

    public abstract List<String> getValues(VariableStore store, String response, String item) throws APIException;

    public String getValue() throws APIException {
        return getValue(null, null, null);
    }

    public String getValue(VariableStore store) throws APIException {
        return getValue(store, null, null);
    }

    public String getValue(VariableStore store, String response) throws APIException {
        return getValue(store, response, null);
    }

    public abstract String getValue(VariableStore store, String response, String item) throws APIException;

    public Object getValueAsObject(VariableStore store, String response) throws APIException {
        return getValue(store, response);
    }
}
