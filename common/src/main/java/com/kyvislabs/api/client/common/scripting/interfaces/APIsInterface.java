package com.kyvislabs.api.client.common.scripting.interfaces;

import com.inductiveautomation.ignition.common.rpc.RpcInterface;
import com.kyvislabs.api.client.common.exceptions.APIException;
import org.python.core.PyDictionary;

@RpcInterface(packageId = "apis.kyvislabs")
public interface APIsInterface {
    void invokeFunction(String apiName, String functionName, PyDictionary functionParameters) throws APIException;
    void updateTag(String tagPath, Object value) throws APIException;
}
