package com.kyvislabs.api.client.gateway.api;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Functions implements YamlParser {
    private Logger logger;
    private API api;
    private Map<String, Function> functions;

    public Functions(API api) {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Functions", api.getName()));
        this.api = api;
        this.functions = new ConcurrentHashMap<>();
    }

    public void parse(Map yamlMap) throws APIException {
        if (yamlMap.containsKey("functions")) {
            Map functionsMap = (Map) yamlMap.get("functions");
            Iterator<String> it = functionsMap.keySet().iterator();
            while (it.hasNext()) {
                String name = it.next();
                Map functionMap = (Map) functionsMap.get(name);
                Function function = new Function(api, name);
                function.parse(functionMap);
                functions.put(name, function);
            }
        }
    }

    public String getStatus() {
        int running = 0;
        int unknown = 0;
        int failed = 0;
        for (Function function : functions.values()) {
            if (function.getStatus().equals(Function.FunctionStatus.SUCCESS)) {
                running += 1;
            } else if (function.getStatus().equals(Function.FunctionStatus.UNKNOWN)) {
                unknown += 1;
            } else {
                failed += 1;
            }
        }

        String failedStr = String.format("%d failed", failed);
        if (failed > 0) {
            failedStr = String.format("<font color=red>%d failed</font>", failed);
        }

        return String.format("%d running<br>%s<br>%d unknown", running, failedStr, unknown);
    }

    public boolean functionExists(String name) {
        return functions.containsKey(name);
    }

    public Function getFunction(String name) throws APIException {
        if (functions.containsKey(name)) {
            return functions.get(name);
        }

        throw new APIException("Function '" + name + "' doesn't exist");
    }

    public void expired() {
        logger.debug("Functions trial expired");
        for (Function function : functions.values()) {
            function.setStatus(Function.FunctionStatus.TRIAL_EXPIRED);
        }
    }

    public void disable() {
        logger.debug("Functions disable");
        for (Function function : functions.values()) {
            function.setStatus(Function.FunctionStatus.DISABLED);
        }
    }

    public void startup() throws APIException {
        logger.debug("Functions starting up");
        for (Function function : functions.values()) {
            function.startup();
        }
    }

    public void shutdown() {
        logger.debug("Functions shutting down");
        for (Function function : functions.values()) {
            try {
                function.shutdown();
            } catch (Throwable ex) {
                logger.error("Error shutting down", ex);
            }
        }
    }
}