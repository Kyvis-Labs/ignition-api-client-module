package com.kyvislabs.api.client.gateway.api;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.*;

public class Headers implements YamlParser {
    private API api;
    private List<Header> headers;

    public Headers(API api) {
        this.api = api;
        this.headers = Collections.synchronizedList(new ArrayList<>());
    }

    public void parse(Map yamlMap) throws APIException {
        if (yamlMap.containsKey("headers")) {
            List headersList = (List) yamlMap.get("headers");
            for (Object headerObj : headersList) {
                Map headerMap = (Map) headerObj;
                if (!headerMap.containsKey("key") || !headerMap.containsKey("value")) {
                    throw new APIException("Header missing key/value pair: " + headerMap.toString());
                }
                headers.add(new Header(headerMap.get("key").toString(), ValueString.parseValueString(api, headerMap, "value", true)));
            }
        }
    }

    public synchronized Map<String, Object> getHeadersMap() throws APIException {
        return getHeadersMap(null);
    }

    public synchronized Map<String, Object> getHeadersMap(VariableStore store) throws APIException {
        Map<String, Object> headersMap = new HashMap<>();
        for (Header header : headers) {
            headersMap.put(header.getKey(), header.getValue().getValue(store));
        }
        return headersMap;
    }

    public class Header {
        private String key;
        private ValueString value;

        public Header(String key, ValueString value) {
            this.key = key;
            this.value = value;
        }

        public synchronized String getKey() {
            return key;
        }

        public synchronized ValueString getValue() {
            return value;
        }
    }
}