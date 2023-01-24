package com.kyvislabs.api.client.gateway.api;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.authentication.AbstractAuthType;
import com.kyvislabs.api.client.gateway.api.authentication.AuthTypeInterface;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.Map;

public class AuthType implements YamlParser, AuthTypeInterface {
    private API api;
    private AbstractAuthType authType;

    public AuthType(API api) {
        this.api = api;
    }

    public void parse(Integer version, Map yamlMap) throws APIException {
        this.authType = AbstractAuthType.getAuthType(api, version, yamlMap);
    }

    @Override
    public void initializeVariables() throws APIException {
        authType.initializeVariables();
    }

    public synchronized AbstractAuthType getAuthType() {
        return authType;
    }

    @Override
    public synchronized Map<String, Object> getHeadersMap() throws APIException {
        return authType.getHeadersMap();
    }

    @Override
    public synchronized boolean requiresSession() {
        return authType.requiresSession();
    }

    @Override
    public synchronized boolean isAuthenticated() throws APIException {
        return authType.isAuthenticated();
    }

    @Override
    public synchronized boolean isAuthorized() throws APIException {
        return authType.isAuthorized();
    }

    @Override
    public void authenticate(VariableStore store) throws APIException {
        authType.authenticate(store);
    }
}
