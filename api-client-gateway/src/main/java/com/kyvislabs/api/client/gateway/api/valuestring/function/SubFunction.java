package com.kyvislabs.api.client.gateway.api.valuestring.function;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubFunction extends ValueStringFunction {
    private Integer start, end;

    public SubFunction(ValueString valueString, List<String> functionParts) throws APIException {
        super(valueString, functionParts);
        parse();
    }

    private void parse() throws APIException {
        if (getFunctionPartsSize() < 1) {
            throw new APIException("Sub function missing parameters");
        }

        Pattern r = Pattern.compile("\\[(-?\\d*)(\\:?)(-?\\d*)\\]");
        Matcher m = r.matcher(getFunctionPart(0));
        if (m.find()) {
            String g1 = m.group(1);
            String g3 = m.group(3);
            boolean hasColon = m.group(2).equals(":");

            start = g1.equals("") ? 0 : Integer.valueOf(g1);
            if (!hasColon) {
                end = null;
            } else {
                end = g3.equals("") ? Integer.MAX_VALUE : Integer.valueOf(g3);
            }
        }
    }

    private synchronized Integer getStart() {
        return start;
    }

    private synchronized Integer getEnd() {
        return end;
    }

    @Override
    public String getValue(String value) {
        Integer start = getStart();
        Integer end = getEnd();

        if (value != null) {
            if (start != null && end == null) {
                if (start < 0) {
                    return value.substring(value.length() + start, value.length() + start + 1);
                } else {
                    return value.substring(start, start + 1);
                }
            } else {
                int startIndex = start;
                int endIndex = end > value.length() ? value.length() : end;

                if (end < 0) {
                    endIndex = endIndex + end;
                } else {
                    endIndex = end + 1;
                }

                return value.substring(startIndex, endIndex);
            }
        }

        return value;
    }
}
