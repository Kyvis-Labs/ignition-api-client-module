package com.kyvislabs.api.client.gateway.api.functions.actions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.runif.Condition;
import com.kyvislabs.api.client.gateway.api.functions.actions.runif.StoreFileIdNotExists;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.Map;

public abstract class RunIf implements YamlParser {
    protected Function function;

    public RunIf(Function function) {
        this.function = function;
    }

    public static final RunIf getRunIf(Function function, Map yamlMap) {
        RunIf runIf = null;

        if (yamlMap.containsKey("runIf")) {
            Map runIfMap = (Map) yamlMap.get("runIf");
            String type = runIfMap.get("type").toString().toLowerCase();

            if (type.equals(StoreFileIdNotExists.TYPE)) {
                runIf = new StoreFileIdNotExists(function);
            } else if (type.equals(Condition.TYPE)) {
                runIf = new Condition(function);
            }
        }

        return runIf;
    }

    public abstract boolean proceed(VariableStore store, String response) throws APIException;
}