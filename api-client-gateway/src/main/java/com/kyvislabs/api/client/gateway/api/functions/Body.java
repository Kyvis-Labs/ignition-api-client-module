package com.kyvislabs.api.client.gateway.api.functions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;
import net.dongliu.requests.RequestBuilder;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class Body implements YamlParser {
    private Function function;
    private BodyType type;
    private ValueString value;
    private String contentType;
    private List<Parameter> parameters;

    public Body(Function function) {
        this.function = function;
    }

    private synchronized BodyType getType() {
        return type;
    }

    private synchronized ValueString getValue() {
        return value;
    }

    private synchronized String getContentType() {
        return contentType;
    }

    private synchronized List<Parameter> getParameters() {
        return parameters;
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        if (yamlMap.containsKey("body")) {
            Map bodyMap = (Map) yamlMap.get("body");
            type = BodyType.valueOf(bodyMap.getOrDefault("type", "none").toString().toUpperCase());
            value = ValueString.parseValueString(function.getApi(), bodyMap, "value");
            contentType = (String) bodyMap.getOrDefault("contentType", null);
            this.parameters = Parameter.parseParameters(function.getApi(), bodyMap);
        } else {
            type = BodyType.NONE;
            value = null;
            contentType = null;
            this.parameters = Collections.synchronizedList(new ArrayList<>());
        }
    }

    public Map<String, Object> getHeadersMap() {
        Map<String, Object> headersMap = new HashMap<>();
        if (getType().equals(BodyType.JSON)) {
            headersMap.put("Content-Type", "application/json");
        } else if (getContentType() != null) {
            headersMap.put("Content-Type", getContentType());
        }
        return headersMap;
    }

    public String build(RequestBuilder builder, VariableStore store) throws APIException {
        String body = null;
        if (getType().equals(BodyType.NONE)) {
            return null;
        } else if (getType().equals(BodyType.FORM)) {
            List<net.dongliu.requests.Parameter<Object>> params = Parameter.getParameters(getParameters(), store);
            body = params.stream()
                    .map(key -> key.name() + "=" + key.value().toString()).collect(Collectors.joining(", ", "{", "}"));
            builder.body(params);
        } else {
            body = getBody(store);
            if (body != null) {
                builder.body(body);
            }
        }

        return body;
    }

    private String getBody(VariableStore store) throws APIException {
        try {
            if (getType().equals(BodyType.JSON)) {
                JSONObject retObj = null;
                if (getParameters().size() > 0) {
                    retObj = new JSONObject();
                    for (Parameter parameter : getParameters()) {
                        retObj.put(parameter.getName(), parameter.getValue(store));
                    }
                } else {
                    retObj = new JSONObject(getValue().getValue(store));
                }
                return retObj.toString();
            } else if (getType().equals(BodyType.TEXT)) {
                return getValue().getValue(store);
            }

            return null;
        } catch (Throwable ex) {
            throw new APIException("Error getting body", ex);
        }
    }

    public enum BodyType {
        NONE,
        TEXT,
        JSON,
        FORM
    }
}
