package com.kyvislabs.api.client.gateway.api.valuestring.command;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.List;

public class JsonPathCommand extends ValueStringCommand {
    private String jsonPath;
    private boolean commandItem = false;

    public JsonPathCommand(ValueString valueString, List<String> commandParts) throws APIException {
        super(valueString, commandParts);
        parse();
    }

    private void parse() throws APIException {
        if (getCommandPartsSize() < 1) {
            throw new APIException("JSON path command cannot be empty");
        }

        int jsonPathIndex = 0;
        if (getCommandPartsSize() > 1) {
            jsonPathIndex = 1;
            commandItem = true;
        }

        jsonPath = getCommandPart(jsonPathIndex);
    }

    public synchronized String getJsonPath() {
        return jsonPath;
    }

    public synchronized boolean isCommandItem() {
        return commandItem;
    }

    @Override
    public List<String> getValues(VariableStore store, String response, String item) {
        Configuration conf = Configuration.builder()
                .options(Option.AS_PATH_LIST, Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS).build();
        return JsonPath.using(conf).parse(response).read(getJsonPath());
    }

    @Override
    public String getValue(VariableStore store, String response, String item) throws APIException {
        String path = getJsonPath();
        if (isCommandItem()) {
            if (item != null) {
                path = item + path.replace("$", "");
            } else {
                throw new APIException("JSON path command missing item");
            }
        }
        return JsonPath.parse(response).read(path).toString();
    }

    @Override
    public Object getValueAsObject(VariableStore store, String response) {
        return JsonPath.parse(response).read(getJsonPath());
    }
}
