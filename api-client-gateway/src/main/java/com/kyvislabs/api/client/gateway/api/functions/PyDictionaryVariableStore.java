package com.kyvislabs.api.client.gateway.api.functions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.python.core.PyDictionary;

public class PyDictionaryVariableStore implements VariableStore {
    private PyDictionary parameters;

    public PyDictionaryVariableStore(PyDictionary parameters) {
        this.parameters = parameters;
    }

    @Override
    public String getStoreName() {
        return "handler";
    }

    @Override
    public Object getVariable(String name) throws APIException {
        if (parameters.containsKey(name)) {
            return parameters.get(name);
        }

        throw new APIException("Variable '" + name + "' doesn't exist");
    }

    @Override
    public void setVariable(String name, Object value) throws APIException {
        // no-op
    }
}
