package com.kyvislabs.api.client.common.scripting;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.interfaces.APIsInterface;
import org.python.core.PyDictionary;

/**
 * Wraps an APIsInterface implementation in an AbstractScriptFunctionsScriptModule.
 * Used by GatewayHook for the direct (non-RPC) gateway script registration.
 */
public class ScriptFunctionsScriptModulePyWrapper extends AbstractScriptFunctionsScriptModule {

    private final APIsInterface delegate;

    public ScriptFunctionsScriptModulePyWrapper(APIsInterface delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void invokeFunctionImpl(String apiName, String functionName, PyDictionary functionParameters) throws APIException {
        delegate.invokeFunction(apiName, functionName, functionParameters);
    }

    @Override
    protected void updateTagImpl(String tagPath, Object value) throws APIException {
        delegate.updateTag(tagPath, value);
    }
}
