package com.kyvislabs.api.client.gateway.api.valuestring;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.valuestring.command.*;
import com.kyvislabs.api.client.gateway.api.valuestring.function.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommandString extends ValueStringPart {
    private ValueStringCommand commandValue;
    private List<ValueStringFunction> functions;

    public CommandString(ValueString valueString, String part) throws APIException {
        super(valueString, part);
        this.functions = Collections.synchronizedList(new ArrayList<>());
        parse();
    }

    private void parse() throws APIException {
        List<String> parts = Arrays.stream(getPart().split("\\|"))
                .map(String::trim).collect(Collectors.toList());

        List<String> commandParts = Arrays.stream(parts.get(0).split("\\:\\:"))
                .map(String::trim).collect(Collectors.toList());

        this.commandValue = Command.getCommandFromString(commandParts.get(0)).getCommandValue(getValueString(), commandParts);

        for (int i = 1; i < parts.size(); i++) {
            List<String> functionParts = Arrays.asList(parts.get(i).split("\\:\\:"));
            this.functions.add(Function.getFunctionFromString(functionParts.get(0).trim()).getFunctionValue(getValueString(), functionParts));
        }
    }

    private synchronized ValueStringCommand getCommandValue() {
        return commandValue;
    }

    @Override
    public List<String> getValues(VariableStore store, String response, String item) throws APIException {
        return getCommandValue().getValues(store, response, item);
    }

    @Override
    public String getValue(VariableStore store, String response, String item) throws APIException {
        String value = getCommandValue().getValue(store, response, item);
        for (ValueStringFunction function : functions) {
            value = function.getValue(value);
        }
        return value;
    }

    @Override
    public Object getValueAsObject(VariableStore store, String response) throws APIException {
        return getCommandValue().getValueAsObject(store, response);
    }

    public enum Command {
        RESPONSE("response", ResponseCommand.class),
        ITEM("item", ItemCommand.class),
        ARRAY("array", ArrayCommand.class),
        VAR("var", VariableCommand.class),
        JSONPATH("jsonPath", JsonPathCommand.class);

        private String command;
        private Class clz;

        Command(String command, Class clz) {
            this.command = command;
            this.clz = clz;
        }

        public synchronized String getCommand() {
            return command;
        }

        public synchronized Class getClz() {
            return clz;
        }

        public static Command getCommandFromString(String commandString) throws APIException {
            if (commandString == null) {
                throw new APIException("Value string Command cannot be NULL");
            }

            for (Command command : values()) {
                if (command.getCommand().toLowerCase().equals(commandString.toLowerCase())) {
                    return command;
                }
            }

            throw new APIException("Value string command '" + commandString + "' doesn't exist");
        }

        public synchronized ValueStringCommand getCommandValue(ValueString valueString, List<String> commandParts) throws APIException {
            try {
                return (ValueStringCommand) getClz().getConstructor(ValueString.class, List.class).newInstance(valueString, commandParts);
            } catch (Throwable ex) {
                throw new APIException("Error invoking value string command '" + getCommand() + "'");
            }
        }
    }

    public enum Function {
        TAGPATH("tagPath", TagPathFunction.class),
        SPLIT("split", SplitFunction.class),
        REPLACE("replace", ReplaceFunction.class),
        SUB("sub", SubFunction.class),
        UPPER("upper", UpperFunction.class),
        LOWER("lower", LowerFunction.class),
        TRIM("trim", TrimFunction.class);

        private String function;
        private Class clz;

        Function(String function, Class clz) {
            this.function = function;
            this.clz = clz;
        }

        public String getFunction() {
            return function;
        }

        public synchronized Class getClz() {
            return clz;
        }

        public static Function getFunctionFromString(String functionString) throws APIException {
            if (functionString == null) {
                throw new APIException("Value string function cannot be NULL");
            }

            for (Function function : values()) {
                if (function.getFunction().toLowerCase().equals(functionString.toLowerCase())) {
                    return function;
                }
            }

            throw new APIException("Value string function '" + functionString + "' doesn't exist");
        }

        public synchronized ValueStringFunction getFunctionValue(ValueString valueString, List<String> functionParts) throws APIException {
            try {
                return (ValueStringFunction) getClz().getConstructor(ValueString.class, List.class).newInstance(valueString, functionParts);
            } catch (Throwable ex) {
                throw new APIException("Error invoking value string function '" + getFunction() + "'");
            }
        }
    }
}
