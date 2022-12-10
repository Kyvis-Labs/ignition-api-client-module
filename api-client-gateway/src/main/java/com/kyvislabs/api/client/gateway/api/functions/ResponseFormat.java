package com.kyvislabs.api.client.gateway.api.functions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ResponseFormat implements YamlParser {
    private Logger logger;
    private Function function;
    private ResponseFormatType type;
    private ValueString value;

    public ResponseFormat(Function function) {
        this.function = function;
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.ResponseFormat", function.getApi().getName(), function.getLoggerName()));
    }

    private synchronized ValueString getValue() {
        return value;
    }

    private synchronized ResponseFormatType getType() {
        return type;
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        this.value = null;
        this.type = ResponseFormatType.NONE;

        if (yamlMap.containsKey("reponseFormat")) {
            Map responseFormatMap = (Map) yamlMap.get("reponseFormat");
            this.value = ValueString.parseValueString(function.getApi(), responseFormatMap, "value", true);
            this.type = ResponseFormatType.valueOf(responseFormatMap.getOrDefault("type", "none").toString().toUpperCase());
        }
    }

    public String format(VariableStore store, String response) throws APIException {
        logger.debug("Original response [response=" + response + "]");

        if (getValue() != null) {
            response = getValue().getValue(store, response);
        }

        logger.debug("Value response=" + response + "]");

        if (getType().equals(ResponseFormatType.B64DECODE)) {
            response = new String(Base64.decodeBase64(response));
        }

        logger.debug("Processed response  [type=" + getType().toString() + ", response=" + response + "]");

        return response;
    }

    public enum ResponseFormatType {
        NONE,
        B64DECODE;
    }
}
