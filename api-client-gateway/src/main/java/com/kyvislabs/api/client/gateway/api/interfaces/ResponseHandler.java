package com.kyvislabs.api.client.gateway.api.interfaces;

import com.kyvislabs.api.client.common.exceptions.APIException;

public interface ResponseHandler {
    public void handleResponse(VariableStore store, int statusCode, String contentType, String response) throws APIException;
}
