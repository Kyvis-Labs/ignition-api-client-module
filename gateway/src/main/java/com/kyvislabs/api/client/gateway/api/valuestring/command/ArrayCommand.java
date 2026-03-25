package com.kyvislabs.api.client.gateway.api.valuestring.command;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ArrayCommand extends ValueStringCommand {
    List<String> values;

    public ArrayCommand(ValueString valueString, List<String> commandParts) throws APIException {
        super(valueString, commandParts);
        parse();
    }

    private void parse() throws APIException {
        if (getCommandPartsSize() < 1) {
            throw new APIException("Array command cannot be empty");
        }

        String command = getCommandPart(0);
        command = command.replace("[", "");
        command = command.replace("]", "");
        values = Collections.synchronizedList(Arrays.asList(StringUtils.stripAll(command.split(","))));
    }

    @Override
    public List<String> getValues(VariableStore store, String response, String item) {
        return values;
    }

    @Override
    public String getValue(VariableStore store, String response, String item) throws APIException {
        throw new APIException("Method not implemented");
    }
}
