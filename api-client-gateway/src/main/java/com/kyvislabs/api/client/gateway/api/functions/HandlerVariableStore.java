package com.kyvislabs.api.client.gateway.api.functions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HandlerVariableStore implements VariableStore {
    private Map<String, Object> localVariables;

    public HandlerVariableStore() {
        this.localVariables = new ConcurrentHashMap<>();
    }

    public HandlerVariableStore(Map<String, Object> localVariables) {
        this.localVariables = localVariables;
    }

    public void storeVariable(String name, Object value) {
        this.localVariables.put(name, value);
    }

    @Override
    public String getStoreName() {
        return "handler";
    }

    @Override
    public Object getVariable(String name) throws APIException {
        if (localVariables.containsKey(name)) {
            return localVariables.get(name);
        }

        throw new APIException("Variable '" + name + "' doesn't exist");
    }

    @Override
    public void setVariable(String name, Object value) throws APIException {
        // no-op
    }
}
