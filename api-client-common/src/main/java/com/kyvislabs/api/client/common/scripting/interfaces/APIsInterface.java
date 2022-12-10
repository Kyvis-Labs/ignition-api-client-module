package com.kyvislabs.api.client.common.scripting.interfaces;

import com.kyvislabs.api.client.common.exceptions.APIException;
import org.python.core.PyDictionary;

public interface APIsInterface {

    public void invokeFunction(String apiName, String functionName, PyDictionary functionParameters) throws APIException;

    public void updateTag(String tagPath, Object value) throws APIException;

}
