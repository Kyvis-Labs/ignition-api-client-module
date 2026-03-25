package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.HashMap;
import java.util.Map;

public class Bearer extends AbstractAuthType {
    public static final String AUTH_TYPE = "bearer";
    public static final String VARIABLE_TOKEN = "authType-bearer-token";

    public Bearer(API api) {
        super(api);
    }

    @Override
    public void initializeVariables() {
        api.getVariables().setVariable(VARIABLE_TOKEN, true, false, true);
    }

    @Override
    public synchronized Map<String, Object> getHeadersMap() throws APIException {
        Map<String, Object> headersMap = new HashMap<>();
        headersMap.put("Authorization", "Bearer " + api.getVariables().getVariable(VARIABLE_TOKEN));
        return headersMap;
    }

    @Override
    public synchronized boolean isAuthenticated() {
        return true;
    }

    @Override
    public void authenticate(VariableStore store) {
        // no-op
    }
}
