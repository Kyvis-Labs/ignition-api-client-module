package com.kyvislabs.api.client.gateway.api.valuestring.function;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;

import java.util.List;

public class ReplaceFunction extends ValueStringFunction {
    private String search, replace;

    public ReplaceFunction(ValueString valueString, List<String> functionParts) throws APIException {
        super(valueString, functionParts);
        parse();
    }

    private void parse() throws APIException {
        if (getFunctionPartsSize() < 2) {
            throw new APIException("Replace function missing parameters");
        }

        search = getFunctionPart(0);
        replace = getFunctionPart(1);
    }

    private synchronized String getSearch() {
        return search;
    }

    private synchronized String getReplace() {
        return replace;
    }

    @Override
    public String getValue(String value) {
        return value.replace(getSearch(), getReplace());
    }
}
