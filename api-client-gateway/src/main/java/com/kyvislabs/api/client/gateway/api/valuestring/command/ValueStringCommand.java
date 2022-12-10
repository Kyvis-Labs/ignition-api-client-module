package com.kyvislabs.api.client.gateway.api.valuestring.command;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.ValueHandler;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.Collections;
import java.util.List;

public abstract class ValueStringCommand extends ValueHandler {
    private ValueString valueString;
    private List<String> commandParts;

    public ValueStringCommand(ValueString valueString, List<String> commandParts) {
        this.valueString = valueString;
        this.commandParts = Collections.synchronizedList(commandParts);
    }

    protected synchronized String getCommand() {
        return commandParts.size() > 0 ? commandParts.get(0) : null;
    }

    protected synchronized String getCommandPart(int index) {
        return commandParts.get(index + 1);
    }

    protected synchronized int getCommandPartsSize() {
        return commandParts.size() - 1;
    }

    public synchronized ValueString getValueString() {
        return valueString;
    }

    @Override
    public List<String> getValues(VariableStore store, String response, String item) throws APIException {
        throw new APIException("Method not implemented");
    }
}
