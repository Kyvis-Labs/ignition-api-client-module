package com.kyvislabs.api.client.gateway.api.functions.actions.actions;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.python.core.PyInteger;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ScriptAction extends Action {
    public static final String ACTION = "script";

    private Logger logger;
    private ValueString project;
    private ValueString script;

    public ScriptAction(Function function) {
        super(function);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Action.Script", function.getApi().getName(), function.getLoggerName()));
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        super.parse(version, yamlMap);

        if (!yamlMap.containsKey("script")) {
            throw new APIException("Script missing");
        }

        this.project = ValueString.parseValueString(getFunction().getApi(), yamlMap, "project");
        this.script = ValueString.parseValueString(getFunction().getApi(), yamlMap, "script");
    }

    private synchronized ValueString getProject() {
        return project;
    }

    private synchronized ValueString getScript() {
        return script;
    }

    @Override
    public void handleResponse(VariableStore store, int statusCode, String contentType, String response) throws APIException {
        try {
            logger.debug("Handling script action with [project=" + getProject() + ", script=" + getScript() + "]");

            ScriptManager scriptManager = null;

            if (getProject() == null) {
                scriptManager = function.getApi().getGatewayContext().getScriptManager();
            } else {
                scriptManager = function.getApi().getGatewayContext().getProjectManager().getProjectScriptManager(getProject().getValue(store, response));
            }

            final PyStringMap pyLocals = scriptManager.createLocalsMap();
            final PyStringMap pyGlobals = scriptManager.getGlobals();
            pyLocals.__setitem__("statusCode", new PyInteger(statusCode));
            pyLocals.__setitem__("contentType", new PyString(contentType));
            pyLocals.__setitem__("response", new PyString(response));
            String code = getScript().getValue(store, response) + "(statusCode, contentType, response)";
            scriptManager.runCode(code, pyLocals, pyGlobals, "APIScriptAction");
        } catch (Throwable ex) {
            throw new APIException("Error handling script action", ex);
        }
    }
}
