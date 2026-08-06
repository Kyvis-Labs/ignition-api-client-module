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
    // Guards authenticate() below. FunctionExecutor calls authenticate() from independent scheduled
    // threads (one per function), and neither this class nor OAuth2/TokenAuth's own authenticate()
    // implementations were synchronized - two functions detecting "not authenticated" at once could
    // both kick off a token/code exchange concurrently. For an authorization-code grant the code is
    // single-use, so the loser's exchange fails and its needsAuth() cleanup could wipe out the
    // winner's just-obtained token. Serializing here, plus re-checking isAuthenticated() once the
    // lock is held, means only one thread actually performs the exchange.
    private final Object authLock = new Object();

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
        synchronized (authLock) {
            // Another thread may have already completed authentication while we were waiting for
            // the lock - skip a redundant (and, for authorization-code grants, actively harmful)
            // second exchange. Still lets a forced re-authentication after a 401 through in the
            // common case, since a token that just got refreshed by a concurrent 401 handler will
            // also satisfy this check and correctly make the second caller's retry a no-op.
            if (authType.isAuthenticated()) {
                return;
            }
            authType.authenticate(store);
        }
    }
}
