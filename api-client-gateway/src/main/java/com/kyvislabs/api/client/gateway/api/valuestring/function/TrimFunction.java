package com.kyvislabs.api.client.gateway.api.valuestring.function;

import com.kyvislabs.api.client.gateway.api.ValueString;

import java.util.List;

public class TrimFunction extends ValueStringFunction {
    public TrimFunction(ValueString valueString, List<String> functionParts) {
        super(valueString, functionParts);
    }

    @Override
    public String getValue(String value) {
        return value == null ? null : value.trim();
    }
}
