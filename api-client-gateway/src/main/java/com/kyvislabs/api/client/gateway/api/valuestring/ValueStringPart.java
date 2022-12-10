package com.kyvislabs.api.client.gateway.api.valuestring;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.ValueHandler;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.List;

public abstract class ValueStringPart extends ValueHandler {
    private ValueString valueString;
    private String part;

    public ValueStringPart(ValueString valueString, String part) {
        this.valueString = valueString;
        this.part = part;
    }

    protected synchronized ValueString getValueString() {
        return valueString;
    }

    protected synchronized String getPart() {
        return part;
    }

    @Override
    public List<String> getValues(VariableStore store, String response, String item) throws APIException {
        throw new APIException("Method not implemented");
    }
}
