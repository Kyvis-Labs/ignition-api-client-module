package com.kyvislabs.api.client.gateway.api.valuestring.function;

import com.kyvislabs.api.client.gateway.api.ValueString;

import java.util.List;

public class TagPathFunction extends ValueStringFunction {
    public TagPathFunction(ValueString valueString, List<String> functionParts) {
        super(valueString, functionParts);
    }

    @Override
    public String getValue(String value) {
        // Convert $['store']['book'][0]['title'] to store/book/0/title
        if (value != null) {
            if (value.startsWith("$")) {
                value = value.substring(1);
                value = value.replace("']['", "/").replace("'][", "/").replace("]['", "/").replace("][", "/").replace("['", "").replace("[", "").replace("']", "").replace("]", "");
                return value;
            }
        }

        return value;
    }
}
