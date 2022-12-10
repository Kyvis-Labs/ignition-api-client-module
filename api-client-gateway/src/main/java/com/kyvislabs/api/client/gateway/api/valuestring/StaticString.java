package com.kyvislabs.api.client.gateway.api.valuestring;

import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

public class StaticString extends ValueStringPart {
    public StaticString(ValueString valueString, String part) {
        super(valueString, part);
    }

    @Override
    public String getValue(VariableStore store, String response, String item) {
        return getPart();
    }
}
