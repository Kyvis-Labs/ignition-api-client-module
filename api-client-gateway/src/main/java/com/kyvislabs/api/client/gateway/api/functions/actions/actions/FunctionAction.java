package com.kyvislabs.api.client.gateway.api.functions.actions.actions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.FunctionExecutor;
import com.kyvislabs.api.client.gateway.api.functions.HandlerVariableStore;
import com.kyvislabs.api.client.gateway.api.functions.Retry;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.functions.actions.condition.Case;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FunctionAction extends Action implements VariableStore {
    public static final String ACTION = "function";

    private Logger logger;
    private String trueFunction, falseFunction;
    private FunctionTypeEnum type;
    private ValueString items;
    private List<VariableAction> variables;
    private Map<String, Object> localVariables;
    private Case condition;
    private Retry retry;

    public FunctionAction(Function function) {
        super(function);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Action.Function", function.getApi().getName(), function.getLoggerName()));
        this.localVariables = new ConcurrentHashMap<>();
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        super.parse(version, yamlMap);

        this.type = FunctionTypeEnum.valueOf(yamlMap.getOrDefault("type", "direct").toString().toUpperCase());

        if (type.equals(FunctionTypeEnum.DIRECT) && !yamlMap.containsKey("function")) {
            throw new APIException("Function missing");
        }

        if (type.equals(FunctionTypeEnum.CONDITION) && !yamlMap.containsKey("trueFunction")) {
            throw new APIException("True function missing");
        }

        if (yamlMap.containsKey("function")) {
            this.trueFunction = (String) yamlMap.getOrDefault("function", null);
            this.falseFunction = null;
        } else {
            this.trueFunction = (String) yamlMap.getOrDefault("trueFunction", null);
            this.falseFunction = (String) yamlMap.getOrDefault("falseFunction", null);
        }

        this.items = ValueString.parseItemsValueString(function.getApi(), yamlMap);
        this.variables = VariableAction.parseVariables(version, yamlMap, this);

        condition = null;
        if (type.equals(FunctionTypeEnum.CONDITION)) {
            condition = new Case(logger, function);
            condition.parse(version, yamlMap);
        }

        retry = Retry.parseRetry(function, yamlMap);
    }

    private synchronized String getTrueFunction() {
        return trueFunction;
    }

    private synchronized String getFalseFunction() {
        return falseFunction;
    }

    private synchronized ValueString getItems() {
        return items;
    }

    private synchronized List<VariableAction> getVariables() {
        return variables;
    }

    public synchronized FunctionTypeEnum getType() {
        return type;
    }

    public synchronized Case getCondition() {
        return condition;
    }

    public synchronized Retry getRetry() {
        return retry;
    }

    @Override
    public void handleResponse(VariableStore store, int statusCode, String contentType, String response) throws APIException {
        try {
            for (String item : getItems().getValues(store, response)) {
                if (getType().equals(FunctionTypeEnum.DIRECT)) {
                    executeFunction(store, statusCode, contentType, response, item, getTrueFunction());
                } else if (getType().equals(FunctionTypeEnum.CONDITION)) {
                    if (getCondition().matches(store, response, item)) {
                        executeFunction(store, statusCode, contentType, response, item, getTrueFunction());

                        if (getRetry() != null) {
                            getRetry().clearExecutionCount();
                        }
                    } else {
                        if (getFalseFunction() != null) {
                            executeFunction(store, statusCode, contentType, response, item, getFalseFunction());
                        }

                        if (getRetry() != null) {
                            if (getRetry().canExecute()) {
                                FunctionExecutor executor = new FunctionExecutor(logger, function, new HandlerVariableStore(localVariables));
                                if (getRetry().getDuration() == 0) {
                                    function.getApi().getGatewayContext().getScheduledExecutorService().execute(executor);
                                } else {
                                    Integer duration = getRetry().getDuration();
                                    getRetry().setScheduledFuture(function.getApi().getGatewayContext().getScheduledExecutorService().schedule((Runnable) executor, duration.longValue(), getRetry().getUnit()));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            throw new APIException("Error handling function action", ex);
        }
    }

    @Override
    public void shutdown() {
        if (getRetry() != null && getRetry().getScheduledFuture() != null) {
            getRetry().getScheduledFuture().cancel(true);
        }
    }

    private void executeFunction(VariableStore store, int statusCode, String contentType, String response, String item, String functionName) throws APIException {
        localVariables.clear();
        for (VariableAction variable : getVariables()) {
            Object value = variable.getValue(store, response, item);
            localVariables.put(variable.getName(), value);
        }

        logger.debug("Handling function action with [function=" + functionName + ", variables=" + localVariables.keySet().stream()
                .map(key -> key + "=" + localVariables.get(key).toString())
                .collect(Collectors.joining(", ", "{", "}")) + "]");

        function.getApi().getFunctions().getFunction(functionName).executeBlocking(this);
    }

    @Override
    public String getStoreName() {
        return "handler";
    }

    @Override
    public Object getVariable(String name) throws APIException {
        if (localVariables.containsKey(name)) {
            return localVariables.get(name);
        }

        throw new APIException("Variable '" + name + "' doesn't exist");
    }

    @Override
    public void setVariable(String name, Object value) {
        // no-op
    }

    public enum FunctionTypeEnum {
        DIRECT,
        CONDITION;
    }
}
