package com.kyvislabs.api.client.common.scripting;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.script.hints.ScriptArg;
import com.inductiveautomation.ignition.common.script.hints.ScriptFunction;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.interfaces.APIsInterface;
import org.python.core.PyDictionary;

public abstract class AbstractScriptFunctionsScriptModule implements APIsInterface {

    public static final String MODULE_ID = "com.kyvislabs.api.client";

    static {
        BundleUtil.get().addBundle(
                AbstractScriptFunctionsScriptModule.class.getSimpleName(),
                AbstractScriptFunctionsScriptModule.class.getClassLoader(),
                AbstractScriptFunctionsScriptModule.class.getName().replace('.', '/')
        );
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptFunctionsScriptModule")
    public void invokeFunction(@ScriptArg("apiName") String apiName, @ScriptArg("functionName") String functionName, @ScriptArg("functionParameters") PyDictionary functionParameters) throws APIException {
        invokeFunctionImpl(apiName, functionName, functionParameters);
    }

    protected abstract void invokeFunctionImpl(String apiName, String functionName, PyDictionary functionParameters) throws APIException;

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptFunctionsScriptModule")
    public void updateTag(@ScriptArg("tagPath") String tagPath, @ScriptArg("value") Object value) throws APIException {
        updateTagImpl(tagPath, value);
    }

    protected abstract void updateTagImpl(String tagPath, Object value) throws APIException;

}
