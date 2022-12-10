package com.kyvislabs.api.client.gateway.api.functions.actions.tag;

import com.inductiveautomation.ignition.gateway.tags.managed.WriteHandler;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.VariableAction;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.List;
import java.util.Map;

public class Handler implements YamlParser {
    private Action action;
    private String function;
    private boolean reset;
    private List<VariableAction> variables;

    public Handler(Action action) {
        this.action = action;
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        this.function = (String) yamlMap.getOrDefault("function", null);
        this.reset = (Boolean) yamlMap.getOrDefault("reset", false);
        this.variables = VariableAction.parseVariables(yamlMap, action);
    }

    public synchronized Action getAction() {
        return action;
    }

    public synchronized boolean hasFunction() {
        return function != null;
    }

    public synchronized String getFunction() {
        return function;
    }

    public synchronized boolean isReset() {
        return reset;
    }

    public synchronized List<VariableAction> getVariables() {
        return variables;
    }

    public synchronized WriteHandler getWriteHandler(String parentPath, Tag tag) {
        if (isReset() || hasFunction() || getVariables().size() > 0) {
            return new TagWriteHandler(parentPath, tag);
        }

        return action.getFunction().getApi();
    }
}