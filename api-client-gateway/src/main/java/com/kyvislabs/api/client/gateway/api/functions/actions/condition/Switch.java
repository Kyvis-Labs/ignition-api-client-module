package com.kyvislabs.api.client.gateway.api.functions.actions.condition;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Switch implements YamlParser {
    private Logger logger;
    private Function function;
    private List<Case> cases;

    public Switch(Function function) {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Condition.Switch", function.getApi().getName(), function.getLoggerName()));
        this.function = function;
        this.cases = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        if (yamlMap.containsKey("cases")) {
            List casesList = (List) yamlMap.get("cases");
            for (Object caseObj : casesList) {
                Map caseMap = (Map) caseObj;
                Case switchCase = new Case(logger, function);
                switchCase.parse(caseMap);
                cases.add(switchCase);
            }
        }
    }

    public Case handleResponse(VariableStore store, String response) throws APIException {
        for (Case switchCase : cases) {
            if (switchCase.matches(store, response)) {
                return switchCase;
            }
        }

        return null;
    }
}
