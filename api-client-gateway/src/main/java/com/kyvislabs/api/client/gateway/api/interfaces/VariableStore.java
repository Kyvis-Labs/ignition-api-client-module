package com.kyvislabs.api.client.gateway.api.interfaces;

import com.kyvislabs.api.client.common.exceptions.APIException;

public interface VariableStore {

    String getStoreName();

    Object getVariable(String name) throws APIException;

    void setVariable(String name, Object value) throws APIException;

}
