package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.Map;

public interface AuthTypeInterface {
    public abstract void initializeVariables() throws APIException;

    public abstract Map<String, Object> getHeadersMap() throws APIException;

    public abstract boolean requiresSession();

    public abstract boolean isAuthorized() throws APIException;

    public abstract boolean isAuthenticated() throws APIException;

    public abstract void authenticate(VariableStore store) throws APIException;
}
