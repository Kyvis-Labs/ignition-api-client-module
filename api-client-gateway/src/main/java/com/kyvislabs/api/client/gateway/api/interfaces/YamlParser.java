package com.kyvislabs.api.client.gateway.api.interfaces;

import com.kyvislabs.api.client.common.exceptions.APIException;

import java.util.Map;

public interface YamlParser {
    void parse(Integer version, Map yamlMap) throws APIException;
}
