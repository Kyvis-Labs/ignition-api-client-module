package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.apache.commons.codec.binary.Base64;

import java.util.HashMap;
import java.util.Map;

public class BasicAuth extends AbstractAuthType {
    public static final String AUTH_TYPE = "basic";
    public static final String VARIABLE_USER = "authType-basic-username";
    public static final String VARIABLE_PASSWORD = "authType-basic-password";

    public BasicAuth(API api) {
        super(api);
    }

    @Override
    public void initializeVariables() {
        api.getVariables().setVariable(VARIABLE_USER, true, false, false);
        api.getVariables().setVariable(VARIABLE_PASSWORD, true, false, true);
    }

    @Override
    public synchronized Map<String, Object> getHeadersMap() throws APIException {
        Map<String, Object> headersMap = new HashMap<>();

        String auth = api.getVariables().getVariable(VARIABLE_USER) + ":" + api.getVariables().getVariable(VARIABLE_PASSWORD);
        byte[] encodedAuth = Base64.encodeBase64(
                auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);
        headersMap.put("Authorization", authHeader);
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
