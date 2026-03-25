package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.Headers;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.Parameter;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import net.dongliu.requests.RawResponse;
import net.dongliu.requests.RequestBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TokenAuth extends AbstractAuthType {
    public static final String AUTH_TYPE = "token";
    public static final String VARIABLE_USER = "authType-token-username";
    public static final String VARIABLE_PASSWORD = "authType-token-password";
    public static final String VARIABLE_EXPIRATION = "authType-token-expiration";

    private Logger logger;
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private ValueString url;
    private ValueString usernameKey;
    private ValueString passwordKey;
    private Headers headers;
    private List<Parameter> parameters;
    private Integer expiresIn;
    private List<String> tokens;

    public TokenAuth(API api) {
        super(api);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.TokenAuth", api.getName()));
        this.tokens = Collections.synchronizedList(new ArrayList<>());
        this.headers = new Headers(api);
    }

    @Override
    public void initializeVariables() {
        api.getVariables().setVariable(VARIABLE_USER, true, false, false);
        api.getVariables().setVariable(VARIABLE_PASSWORD, true, false, true);
        api.getVariables().setVariable(VARIABLE_EXPIRATION, false, true, false);
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        if (!yamlMap.containsKey("url")) {
            throw new APIException("TokenAuth: Missing auth URL");
        }

        this.url = ValueString.parseValueString(api, yamlMap, "url", true);
        this.usernameKey = ValueString.parseValueString(api, yamlMap, "usernameKey", "username");
        this.passwordKey = ValueString.parseValueString(api, yamlMap, "passwordKey", "password");
        headers.parse(version, yamlMap);
        this.parameters = Parameter.parseParameters(api, version, yamlMap);
        this.expiresIn = (Integer) yamlMap.getOrDefault("expiresIn", null);

        if (yamlMap.containsKey("tokens")) {
            List codesList = (List) yamlMap.get("tokens");
            for (Object codesObj : codesList) {
                Map codesMap = (Map) codesObj;
                tokens.add((String) codesMap.get("name"));
            }
        }
    }

    @Override
    public synchronized Map<String, Object> getHeadersMap() throws APIException {
        return new HashMap<>();
    }

    private Boolean hasExpired() throws APIException {
        if (getExpiresIn() == null) {
            return false;
        }

        String expirationDateStr = api.getVariables().getVariable(VARIABLE_EXPIRATION);
        try {
            if (expirationDateStr != null) {
                Date expirationDate = df.parse(expirationDateStr);
                logger.debug("Expiration date: " + expirationDateStr + " (" + expirationDate.after(new Date()) + ")");
                if (expirationDate.after(new Date())) {
                    return false;
                }
            }

            return true;
        } catch (Throwable ex) {
            throw new APIException("Token: Error checking expiration date", ex);
        }
    }

    @Override
    public synchronized boolean isAuthenticated() throws APIException {
        return !hasExpired();
    }

    private synchronized ValueString getUrl() {
        return url;
    }

    public synchronized ValueString getUsernameKey() {
        return usernameKey;
    }

    public synchronized ValueString getPasswordKey() {
        return passwordKey;
    }

    public synchronized Headers getHeaders() {
        return headers;
    }

    public synchronized List<Parameter> getParameters() {
        return parameters;
    }

    public synchronized Integer getExpiresIn() {
        return expiresIn;
    }

    public synchronized List<String> getTokens() {
        return tokens;
    }

    @Override
    public void authenticate(VariableStore store) throws APIException {
        String url = getUrl().getValue(store);

        Map<String, Object> headersMap = getHeaders().getHeadersMap(store);
        headersMap.put("Content-Type", "application/json");

        JSONObject bodyObj = new JSONObject();

        try {
            bodyObj.put(getUsernameKey().getValue(store), api.getVariables().getVariable(VARIABLE_USER));
            bodyObj.put(getPasswordKey().getValue(store), api.getVariables().getVariable(VARIABLE_PASSWORD));
            for (net.dongliu.requests.Parameter parameter : Parameter.getParameters(getParameters(), store)) {
                bodyObj.put(parameter.name(), parameter.value());
            }
        } catch (Throwable ex) {
            throw new APIException("Error getting body", ex);
        }

        String body = bodyObj.toString();

        RequestBuilder builder = api.getRequestBuilder(url, Function.Method.POST).headers(headersMap).body(body);
        logger.debug(api.getName() + " request [method=" + Function.Method.POST.toString() + ", url=" + url + ", headers=" + headersMap.keySet().stream()
                .map(key -> key + "=" + headersMap.get(key).toString())
                .collect(Collectors.joining(", ", "{", "}")) + ", params=none, body=" + body + "]");

        RawResponse res = builder.send();
        boolean success = res.statusCode() >= 200 && res.statusCode() <= 299;

        if (success) {
            try {
                String response = res.readToText();
                JSONObject responseObj = new JSONObject(response);

                for (String token : getTokens()) {
                    String tokenValue = responseObj.getString(token);
                    api.getVariables().setVariable("auth-" + token, false, true, true, tokenValue);
                }

                if (getExpiresIn() != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(new Date());
                    cal.add(Calendar.SECOND, getExpiresIn());
                    String expiration = df.format(cal.getTime());
                    api.getVariables().setVariable(VARIABLE_EXPIRATION, expiration);
                }
            } catch (Throwable ex) {
                logger.error("Error parsing response");
            }
        } else {
            logger.debug("Failed authentication (" + res.statusCode() + "): " + res.readToText());
            throw new APIException("Token: Failed login");
        }
    }
}
