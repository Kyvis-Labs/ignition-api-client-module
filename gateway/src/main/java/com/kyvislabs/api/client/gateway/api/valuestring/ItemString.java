package com.kyvislabs.api.client.gateway.api.valuestring;

import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.ArrayList;
import java.util.List;

public class ItemString extends ValueStringPart {
    public ItemString(ValueString valueString) {
        super(valueString, null);
    }

    @Override
    public String getValue(VariableStore store, String response, String item) {
        return null;
    }

    @Override
    public List<String> getValues(VariableStore store, String response, String item) {
        List<String> values = new ArrayList<>();
        values.add(null);
        return values;
    }
}
