package com.kyvislabs.api.client.gateway.api.functions.actions.actions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.webhooks.WebhookKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebhookAction extends Action {
    public static final String ACTION = "webhook";

    private Logger logger;
    private ValueString key;
    private ValueString id;
    private ValueString name;
    private Integer ttl;
    private ValueString items;
    private List<VariableAction> variables;

    public WebhookAction(Function function) {
        super(function);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Action.Webhook", function.getApi().getName(), function.getLoggerName()));
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        super.parse(yamlMap);

        if (!yamlMap.containsKey("name")) {
            throw new APIException("Name missing");
        }

        if (!yamlMap.containsKey("key")) {
            throw new APIException("Key missing");
        }

        this.key = ValueString.parseValueString(function.getApi(), yamlMap, "key", true);
        this.id = ValueString.parseValueString(function.getApi(), yamlMap, "id");
        this.name = ValueString.parseValueString(function.getApi(), yamlMap, "name", true);

        Object ttl = yamlMap.getOrDefault("ttl", null);
        this.ttl = ttl == null ? null : Integer.valueOf(ttl.toString());

        this.items = ValueString.parseItemsValueString(function.getApi(), yamlMap);
        this.variables = VariableAction.parseVariables(yamlMap, this);
    }

    private synchronized ValueString getKey() {
        return key;
    }

    private synchronized ValueString getId() {
        return id;
    }

    private synchronized ValueString getName() {
        return name;
    }

    private synchronized Integer getTtl() {
        return ttl;
    }

    private synchronized ValueString getItems() {
        return items;
    }

    private synchronized List<VariableAction> getVariables() {
        return variables;
    }

    @Override
    public void handleResponse(VariableStore store, int statusCode, String contentType, String response) throws APIException {
        try {
            for (String item : getItems().getValues(store, response)) {
                String key = getKey().getValue(store, response, item);
                String id = getId() != null ? getId().getValue(store, response, item) : null;
                String name = getName().getValue(store, response, item);
                WebhookKey webhookKey = function.getApi().getWebhooks().getWebhook(name).getWebhookKeyOrCreate(key, id, getTtl());
                WebhookActionVariables webhookVars = new WebhookActionVariables(webhookKey);

                for (VariableAction variable : getVariables()) {
                    Object value = variable.getValue(store, response, item);
                    webhookVars.putVariable(variable.getName(), value);
                }

                logger.debug("Handling webhook action with [key=" + key + ", id=" + id + ", name=" + name + "]");
                webhookKey.execute(webhookVars);
            }
        } catch (Throwable ex) {
            throw new APIException("Error handling function action", ex);
        }
    }

    public class WebhookActionVariables implements VariableStore {
        private Map<String, Object> localVariables;
        private WebhookKey webhookKey;

        public WebhookActionVariables(WebhookKey webhookKey) {
            this.localVariables = new ConcurrentHashMap<>();
            this.webhookKey = webhookKey;
        }

        public void putVariable(String name, Object value) {
            localVariables.put(name, value);
        }

        @Override
        public String getStoreName() {
            return "handler";
        }

        @Override
        public Object getVariable(String name) throws APIException {
            try {
                return webhookKey.getVariable(name);
            } catch (Throwable ex) {

            }

            if (localVariables.containsKey(name)) {
                return localVariables.get(name);
            }

            throw new APIException("Variable '" + name + "' doesn't exist");
        }

        @Override
        public void setVariable(String name, Object value) {
            // no-op
        }
    }
}
