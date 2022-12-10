package com.kyvislabs.api.client.gateway.api.valuestring.function;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;

import java.util.List;

public abstract class ValueStringFunction {
    private ValueString valueString;
    private List<String> functionParts;

    public ValueStringFunction(ValueString valueString, List<String> functionParts) {
        this.valueString = valueString;
        this.functionParts = functionParts;
    }

    protected synchronized String getFunction() {
        return functionParts.size() > 0 ? functionParts.get(0) : null;
    }

    protected synchronized String getFunctionPart(int index) {
        return functionParts.get(index + 1);
    }

    protected synchronized int getFunctionPartsSize() {
        return functionParts.size() - 1;
    }

    public synchronized ValueString getValueString() {
        return valueString;
    }

    public abstract String getValue(String value) throws APIException;
}
