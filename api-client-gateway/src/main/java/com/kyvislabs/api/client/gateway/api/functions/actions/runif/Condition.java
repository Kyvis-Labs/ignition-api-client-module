package com.kyvislabs.api.client.gateway.api.functions.actions.runif;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.RunIf;
import com.kyvislabs.api.client.gateway.api.functions.actions.condition.Case;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class Condition extends RunIf {
    public static final String TYPE = "condition";

    private Logger logger;
    private Case condition;

    public Condition(Function function) {
        super(function);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Action.RunIf.Condition", function.getApi().getName(), function.getLoggerName()));
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        condition = new Case(logger, function);
        condition.parse(yamlMap);
    }

    public synchronized Case getCondition() {
        return condition;
    }

    @Override
    public boolean proceed(VariableStore store, String response) throws APIException {
        return getCondition().matches(store, response);
    }
}
