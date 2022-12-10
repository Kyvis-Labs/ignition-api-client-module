package com.kyvislabs.api.client.gateway.api.valuestring.command;

import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.List;

public class ResponseCommand extends ValueStringCommand {
    public ResponseCommand(ValueString valueString, List<String> commandParts) {
        super(valueString, commandParts);
    }

    @Override
    public String getValue(VariableStore store, String response, String item) {
        return response;
    }
}
