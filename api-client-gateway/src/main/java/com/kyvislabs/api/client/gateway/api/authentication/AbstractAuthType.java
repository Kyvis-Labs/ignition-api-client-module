package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.Map;

public abstract class AbstractAuthType implements AuthTypeInterface, YamlParser {
    protected API api;

    public AbstractAuthType(API api) {
        this.api = api;
    }

    public API getApi() {
        return api;
    }

    public static final AbstractAuthType getAuthType(API api, Map yamlMap) throws APIException {
        AbstractAuthType authType = null;
        if (yamlMap.containsKey("authType")) {
            Map authTypeMap = (Map) yamlMap.get("authType");

            String authTypeStr = authTypeMap.get("type").toString();
            if (authTypeStr.equals(NoAuth.AUTH_TYPE)) {
                authType = new NoAuth(api);
            } else if (authTypeStr.equals(Bearer.AUTH_TYPE)) {
                authType = new Bearer(api);
            } else if (authTypeStr.equals(BasicAuth.AUTH_TYPE)) {
                authType = new BasicAuth(api);
            } else if (authTypeStr.equals(OAuth2.AUTH_TYPE)) {
                authType = new OAuth2(api);
            } else if (authTypeStr.equals(SessionAuth.AUTH_TYPE)) {
                authType = new SessionAuth(api);
            } else if (authTypeStr.equals(TokenAuth.AUTH_TYPE)) {
                authType = new TokenAuth(api);
            }

            if (authType == null) {
                throw new APIException("Authentication type '" + authTypeStr + "' not recognized");
            }

            authType.parse(authTypeMap);
        } else {
            authType = new NoAuth(api);
        }

        return authType;
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        // no-op
    }

    @Override
    public boolean isAuthorized() throws APIException {
        return true;
    }

    @Override
    public boolean requiresSession() {
        return false;
    }
}
