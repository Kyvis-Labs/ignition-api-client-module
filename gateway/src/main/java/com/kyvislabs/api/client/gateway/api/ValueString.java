package com.kyvislabs.api.client.gateway.api;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.interfaces.ValueHandler;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.valuestring.CommandString;
import com.kyvislabs.api.client.gateway.api.valuestring.ItemString;
import com.kyvislabs.api.client.gateway.api.valuestring.StaticString;
import com.kyvislabs.api.client.gateway.api.valuestring.ValueStringPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValueString extends ValueHandler {
    private Logger logger;
    private API api;
    private String parameter;
    private List<ValueStringPart> parts;

    public static ValueString parseValueString(API api, Map yamlMap, String key) throws APIException {
        return parseValueString(api, yamlMap, key, false);
    }

    public static ValueString parseValueString(API api, Map yamlMap, String key, String defaultValue) throws APIException {
        return parseValueString(api, yamlMap, key, defaultValue, true);
    }

    public static ValueString parseValueString(API api, Map yamlMap, String key, boolean required) throws APIException {
        return parseValueString(api, yamlMap, key, null, required);
    }

    public static ValueString parseValueString(API api, Map yamlMap, String key, String defaultValue, boolean required) throws APIException {
        if (!required && !yamlMap.containsKey(key) && defaultValue == null) {
            return null;
        }

        if (required && !yamlMap.containsKey(key) && defaultValue == null) {
            throw new APIException("Configuration missing " + key);
        }

        Object parameter = yamlMap.getOrDefault(key, defaultValue);
        return new ValueString(api, parameter == null ? null : parameter.toString());
    }

    public static ValueString parseItemsValueString(API api, Map yamlMap) throws APIException {
        if (yamlMap.containsKey("items")) {
            return new ValueString(api, (String) yamlMap.get("items"));
        } else {
            // If there is no items in the yaml, we need to return a list of 1 item (null)
            ValueString valueString = new ValueString(api, null);
            valueString.addPart(new ItemString(valueString));
            return valueString;
        }
    }

    public ValueString(API api, String parameter) throws APIException {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Parameter.Value", api.getName()));
        this.api = api;
        this.parameter = parameter;
        this.parts = Collections.synchronizedList(new ArrayList<>());
        if (parameter != null) {
            parseParameter();
        }
    }

    public API getApi() {
        return api;
    }

    private synchronized void addPart(ValueStringPart part) {
        parts.add(part);
    }

    private synchronized String getParameter() {
        return parameter;
    }

    private void parseParameter() throws APIException {
        try {
            Pattern r = Pattern.compile("(\\{\\{(.*?)\\}\\})+");
            Matcher m = r.matcher(parameter);
            int i = 0;
            while (m.find()) {
                String part = m.group(1);
                part = part.replace("{{", "");
                part = part.replace("}}", "");
                logger.debug("Found part '" + part + "'");
                if (i < m.start()) {
                    parts.add(new StaticString(this, parameter.substring(i, m.start())));
                }
                parts.add(new CommandString(this, part));
                i = m.end();
            }
            if (i < parameter.length()) {
                parts.add(new StaticString(this, parameter.substring(i)));
            }
        } catch (Throwable ex) {
            throw new APIException("Error parsing value string '" + parameter + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<String> getValues(VariableStore store, String response, String item) throws APIException {
        return parts.get(0).getValues(store, response, item);
    }

    @Override
    public String getValue(VariableStore store, String response, String item) throws APIException {
        StringBuilder builder = new StringBuilder();
        for (ValueStringPart part : parts) {
            builder.append(part.getValue(store, response, item));
        }
        return builder.toString();
    }

    @Override
    public Object getValueAsObject(VariableStore store, String response) throws APIException {
        return parts.get(0).getValueAsObject(store, response);
    }
}
