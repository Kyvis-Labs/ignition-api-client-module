package com.kyvislabs.api.client.gateway.scripting;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.interfaces.APIsInterface;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.PyDictionaryVariableStore;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import org.python.core.PyDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptFunctionsScriptModule implements APIsInterface {
    private final Logger logger = LoggerFactory.getLogger("API.ScriptModule");
    private final GatewayContext gatewayContext;

    public ScriptFunctionsScriptModule(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
    }

    @Override
    public void invokeFunction(String apiName, String functionName, PyDictionary functionParameters) throws APIException {
        logger.debug("invokeFunction called: api=" + apiName + ", function=" + functionName);
        API api = APIManager.get().getAPI(apiName);
        Function function = api.getFunctions().getFunction(functionName);
        function.executeAsync(new PyDictionaryVariableStore(functionParameters));
    }

    @Override
    public void updateTag(String tagPath, Object value) throws APIException {
        logger.debug("updateTag called: path=" + tagPath + ", value=" + value);
        APIManager.get().getTagManager().tagUpdate(tagPath, value);
    }
}
