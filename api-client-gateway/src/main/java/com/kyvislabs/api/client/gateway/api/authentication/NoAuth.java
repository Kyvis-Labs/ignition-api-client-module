package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.HashMap;
import java.util.Map;

public class NoAuth extends AbstractAuthType {
    public static final String AUTH_TYPE = "none";

    public NoAuth(API api) {
        super(api);
    }

    @Override
    public void initializeVariables() {

    }

    @Override
    public Map<String, Object> getHeadersMap() {
        return new HashMap<>();
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
