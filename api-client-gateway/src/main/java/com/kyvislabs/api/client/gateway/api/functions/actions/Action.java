package com.kyvislabs.api.client.gateway.api.functions.actions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.*;
import com.kyvislabs.api.client.gateway.api.interfaces.ResponseHandler;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.Map;

public abstract class Action implements YamlParser, ResponseHandler {
    protected Function function;
    private RunIf runIf;

    public Action(Function function) {
        this.function = function;
    }

    public Function getFunction() {
        return function;
    }

    public static final Action getAction(Function function, Map actionMap) throws APIException {
        String actionType = actionMap.get("action").toString().toLowerCase();
        Action action = null;
        if (actionType.equals(VariableAction.ACTION)) {
            action = new VariableAction(function);
        } else if (actionType.equals(TagAction.ACTION)) {
            action = new TagAction(function);
        } else if (actionType.equals(ScriptAction.ACTION)) {
            action = new ScriptAction(function);
        } else if (actionType.equals(FunctionAction.ACTION)) {
            action = new FunctionAction(function);
        } else if (actionType.equals(WebhookAction.ACTION)) {
            action = new WebhookAction(function);
        } else if (actionType.equals(StoreFileAction.ACTION)) {
            action = new StoreFileAction(function);
        }

        if (action == null) {
            throw new APIException("Action '" + actionType + "' not recognized");
        }

        return action;
    }

    public synchronized RunIf getRunIf() {
        return runIf;
    }

    public void shutdown() {

    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        runIf = RunIf.getRunIf(function, yamlMap);
    }

    public boolean proceed(VariableStore store, String response) throws APIException {
        if (runIf == null) {
            return true;
        }

        return getRunIf().proceed(store, response);
    }
}
