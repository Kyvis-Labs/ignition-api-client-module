package com.kyvislabs.api.client.gateway.scripting;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.AbstractScriptFunctionsScriptModule;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.PyDictionaryVariableStore;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import org.python.core.PyDictionary;

public class ScriptFunctionsScriptModule extends AbstractScriptFunctionsScriptModule {

    private GatewayContext gatewayContext;
    private boolean rpc;

    public ScriptFunctionsScriptModule(GatewayContext gatewayContext, boolean rpc) {
        this.gatewayContext = gatewayContext;
        this.rpc = rpc;
    }

    @Override
    protected void invokeFunctionImpl(String apiName, String functionName, PyDictionary functionParameters) throws APIException {
        API api = APIManager.get().getAPI(apiName);
        Function function = api.getFunctions().getFunction(functionName);
        function.executeAsync(new PyDictionaryVariableStore(functionParameters));
    }

    @Override
    protected void updateTagImpl(String tagPath, Object value) {
        APIManager.get().getTagManager().tagUpdate(tagPath, value);
    }
}
