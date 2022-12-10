package com.kyvislabs.api.client.gateway.api.functions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.interfaces.ResponseHandler;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Actions implements YamlParser, ResponseHandler {
    private Function function;
    private List<Action> actions;

    public Actions(Function function) {
        this.function = function;
        this.actions = Collections.synchronizedList(new ArrayList<>());
    }

    public void parse(Map yamlMap) throws APIException {
        if (yamlMap.containsKey("actions")) {
            List actionsList = (List) yamlMap.get("actions");
            for (Object actionObj : actionsList) {
                Map actionMap = (Map) actionObj;
                Action action = Action.getAction(function, actionMap);
                action.parse(actionMap);
                actions.add(action);
            }
        }
    }

    public void shutdown() {
        for (Action action : actions) {
            action.shutdown();
        }
    }

    @Override
    public void handleResponse(VariableStore store, int statusCode, String contentType, String response) throws APIException {
        for (Action action : actions) {
            if (action.proceed(store, response)) {
                action.handleResponse(store, statusCode, contentType, response);
            }
        }
    }
}