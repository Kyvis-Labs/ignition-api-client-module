package com.kyvislabs.api.client.gateway.api.valuestring.function;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplitFunction extends ValueStringFunction {
    private String separator = "/";
    private Integer start, end;

    public SplitFunction(ValueString valueString, List<String> functionParts) throws APIException {
        super(valueString, functionParts);
        parse();
    }

    private void parse() throws APIException {
        if (getFunctionPartsSize() < 1) {
            throw new APIException("Split function missing parameters");
        }

        if (getFunctionPartsSize() >= 2) {
            separator = getFunctionPart(1);
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

    private synchronized String getSeparator() {
        return separator;
    }

    private synchronized Integer getStart() {
        return start;
    }

    private synchronized Integer getEnd() {
        return end;
    }

    @Override
    public String getValue(String value) {
        String sep = getSeparator();
        Integer start = getStart();
        Integer end = getEnd();

        if (value != null) {
            String[] parts = value.split(sep);
            List<String> newParts = new ArrayList<>();

            if (end == null) {
                if (start < 0) {
                    return parts[parts.length + start];
                } else {
                    return parts[start];
                }
            } else {
                int startIndex = start;
                int endIndex = end;

                if (end < 0) {
                    endIndex = parts.length + end;
                } else {
                    endIndex = end + 1;
                }

                if (endIndex > parts.length) {
                    endIndex = parts.length;
                }

                for (int i = startIndex; i < endIndex; i++) {
                    newParts.add(parts[i]);
                }
            }

            return String.join(sep, newParts);
        }

        return value;
    }
}
