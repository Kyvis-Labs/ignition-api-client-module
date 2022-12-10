package com.kyvislabs.api.client.gateway.api.valuestring.command;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.List;

public class VariableCommand extends ValueStringCommand {
    private static final String TOKEN_API_NAME = "apiName";

    public VariableCommand(ValueString valueString, List<String> commandParts) throws APIException {
        super(valueString, commandParts);

        if (getCommandPartsSize() < 1) {
            throw new APIException("Variable command cannot be empty");
        }
    }

    @Override
    public String getValue(VariableStore store, String response, String item) throws APIException {
        String command = getCommandPart(0);
        if (command.equals(TOKEN_API_NAME)) {
            return getValueString().getApi().getName();
        } else if (command.contains(".")) {
            String section = command.split("\\.")[0];
            String variable = command.split("\\.")[1];

            if (store != null && store.getStoreName().equals(section)) {
                return store.getVariable(variable).toString();
            } else if (getValueString().getApi().getFunctions().functionExists(section)) {
                return getValueString().getApi().getFunctions().getFunction(section).getVariable(variable).toString();
            }
        } else {
            return getValueString().getApi().getVariables().getVariable(command);
        }

        return command;
    }
}
