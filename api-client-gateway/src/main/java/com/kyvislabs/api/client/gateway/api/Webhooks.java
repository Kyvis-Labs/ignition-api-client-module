package com.kyvislabs.api.client.gateway.api;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;
import com.kyvislabs.api.client.gateway.api.webhooks.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Webhooks implements YamlParser {
    private Logger logger;
    private API api;
    private Map<String, Webhook> webhooks;

    public Webhooks(API api) {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Webhooks", api.getName()));
        this.api = api;
        this.webhooks = new ConcurrentHashMap<>();
    }

    public void parse(Map yamlMap) throws APIException {
        if (yamlMap.containsKey("webhooks")) {
            Map webhooksMap = (Map) yamlMap.get("webhooks");
            Iterator<String> it = webhooksMap.keySet().iterator();
            while (it.hasNext()) {
                String name = it.next();
                Map webhookMap = (Map) webhooksMap.get(name);
                Webhook webhook = new Webhook(api, name);
                webhook.parse(webhookMap);
                webhooks.put(name, webhook);
            }
        }
    }

    public String getStatus() {
        int running = 0;
        int waiting = 0;
        int failed = 0;

        if (webhooks.size() == 0) {
            return "";
        }

        for (Webhook webhook : webhooks.values()) {
            if (webhook.getCheck().getStatus().equals(Function.FunctionStatus.SUCCESS)) {
                if (webhook.getAdd().getStatus().equals(Function.FunctionStatus.SUCCESS) || webhook.getAdd().getStatus().equals(Function.FunctionStatus.UNKNOWN)) {
                    if (webhook.getHandle().getStatus().equals(Function.FunctionStatus.SUCCESS)) {
                        running += 1;
                    } else if (webhook.getHandle().getStatus().equals(Function.FunctionStatus.UNKNOWN)) {
                        waiting += 1;
                    } else {
                        failed += 1;
                    }
                } else {
                    failed += 1;
                }
            } else {
                failed += 1;
            }
        }

        String failedStr = String.format("%d failed", failed);
        if (failed > 0) {
            failedStr = String.format("<font color=red>%d failed</font>", failed);
        }

        return String.format("%d running<br>%s<br>%d waiting", running, failedStr, waiting);
    }

    public Webhook getWebhook(String name) throws APIException {
        if (webhooks.containsKey(name)) {
            return webhooks.get(name);
        }

        throw new APIException("Webhook '" + name + "' doesn't exist");
    }

    public void expired() {
        logger.debug("Webhooks trial expired");
        for (Webhook webhook : webhooks.values()) {
            webhook.expired();
        }
    }

    public void disable() {
        logger.debug("Webhooks disable");
        for (Webhook webhook : webhooks.values()) {
            webhook.disable();
        }
    }

    public void startup() throws APIException {
        logger.debug("Webhooks starting up");
        for (Webhook webhook : webhooks.values()) {
            webhook.startup();
        }
    }

    public void shutdown() {
        logger.debug("Webhooks shutting down");
        for (Webhook webhook : webhooks.values()) {
            try {
                webhook.shutdown();
            } catch (Throwable ex) {
                logger.error("Error shutting down", ex);
            }
        }
    }
}