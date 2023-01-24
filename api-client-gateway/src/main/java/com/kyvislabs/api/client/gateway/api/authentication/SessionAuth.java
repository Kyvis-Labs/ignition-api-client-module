package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.Parameter;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import net.dongliu.requests.RawResponse;
import net.dongliu.requests.RequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SessionAuth extends AbstractAuthType {
    public static final String AUTH_TYPE = "session";
    public static final String VARIABLE_USER = "authType-session-username";
    public static final String VARIABLE_PASSWORD = "authType-session-password";

    private Logger logger;
    private boolean authenticated = false;
    private ValueString url;
    private List<Parameter> parameters;

    public SessionAuth(API api) {
        super(api);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.SessionAuth", api.getName()));
    }

    @Override
    public void initializeVariables() {
        api.getVariables().setVariable(VARIABLE_USER, true, false, false);
        api.getVariables().setVariable(VARIABLE_PASSWORD, true, false, true);
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        if (!yamlMap.containsKey("url")) {
            throw new APIException("SessionAuth: Missing auth URL");
        }

        this.url = ValueString.parseValueString(api, yamlMap, "url", true);
        this.parameters = Parameter.parseParameters(api, version, yamlMap);
    }

    @Override
    public synchronized Map<String, Object> getHeadersMap() throws APIException {
        return new HashMap<>();
    }

    @Override
    public synchronized boolean isAuthenticated() {
        return authenticated;
    }

    public synchronized void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    private synchronized ValueString getUrl() {
        return url;
    }

    public synchronized List<Parameter> getParameters() {
        return parameters;
    }

    @Override
    public void authenticate(VariableStore store) throws APIException {
        String url = getUrl().getValue(store);
        List<net.dongliu.requests.Parameter<Object>> params = new ArrayList<>();
        params.add(net.dongliu.requests.Parameter.of("username", api.getVariables().getVariable(VARIABLE_USER)));
        params.add(net.dongliu.requests.Parameter.of("password", api.getVariables().getVariable(VARIABLE_PASSWORD)));
        params.addAll(Parameter.getParameters(getParameters(), store));
        RequestBuilder builder = api.getRequestBuilder(url, Function.Method.POST).body(params);

        logger.debug(api.getName() + " request [method=" + Function.Method.POST.toString() + ", url=" + url + ", headers=none, params=none, body=" + params.stream()
                .map(key -> key.name() + "=" + key.value().toString()).collect(Collectors.joining(", ", "{", "}")) + "]");

        RawResponse res = builder.send();
        boolean success = res.statusCode() >= 200 && res.statusCode() <= 299;

        if (success) {
            setAuthenticated(true);
        } else {
            logger.debug("Failed authentication (" + res.statusCode() + "): " + res.readToText());
            setAuthenticated(false);
            throw new APIException("SessionAuth: Failed login");
        }
    }

    @Override
    public boolean requiresSession() {
        return true;
    }
}
