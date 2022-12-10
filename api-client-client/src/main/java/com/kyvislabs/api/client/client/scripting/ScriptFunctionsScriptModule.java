package com.kyvislabs.api.client.client.scripting;

import com.inductiveautomation.ignition.client.gateway_interface.ModuleRPCFactory;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.AbstractScriptFunctionsScriptModule;
import com.kyvislabs.api.client.common.scripting.interfaces.APIsInterface;
import org.python.core.PyDictionary;

public class ScriptFunctionsScriptModule extends AbstractScriptFunctionsScriptModule {

    private final APIsInterface rpc;

    public ScriptFunctionsScriptModule() {
        rpc = ModuleRPCFactory.create(
                AbstractScriptFunctionsScriptModule.MODULE_ID,
                APIsInterface.class
        );
    }

    @Override
    protected void invokeFunctionImpl(String apiName, String functionName, PyDictionary functionParameters) throws APIException {
        rpc.invokeFunction(apiName, functionName, functionParameters);
    }

    @Override
    protected void updateTagImpl(String tagPath, Object value) throws APIException {
        rpc.updateTag(tagPath, value);
    }
}
