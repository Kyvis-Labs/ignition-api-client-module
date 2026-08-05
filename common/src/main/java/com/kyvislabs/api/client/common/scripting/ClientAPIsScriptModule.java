package com.kyvislabs.api.client.common.scripting;

import com.inductiveautomation.ignition.common.rpc.proto.ProtoRpcSerializer;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.interfaces.APIsInterface;
import org.python.core.PyDictionary;

/**
 * Client-side RPC proxy wrapper for the APIsInterface.
 * Used by ClientHook and DesignerHook to call gateway-side implementations.
 */
public class ClientAPIsScriptModule extends AbstractScriptFunctionsScriptModule {

    public static final ProtoRpcSerializer SERIALIZER = ProtoRpcSerializer.DEFAULT_INSTANCE;

    private final APIsInterface rpc;

    public ClientAPIsScriptModule(APIsInterface rpc) {
        this.rpc = rpc;
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
