package com.kyvislabs.api.client.gateway.api.functions.actions.tag;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.List;
import java.util.Map;

public class UDT implements YamlParser {
    private Action action;
    private String id;
    private ValueString defPath;
    private ValueString items;
    private ValueString path;
    private ValueString name;
    private List<Tag> tags;

    public UDT(Action action) {
        this.action = action;
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        if (!yamlMap.containsKey("id")) {
            throw new APIException("Tag UDT missing id");
        }

        this.id = (String) yamlMap.get("id");
        this.defPath = ValueString.parseValueString(action.getFunction().getApi(), yamlMap, "defPath", action.getFunction().getApi().getName() + "/" + id);
        this.items = ValueString.parseItemsValueString(action.getFunction().getApi(), yamlMap);
        this.path = ValueString.parseValueString(action.getFunction().getApi(), yamlMap, "path", "");
        this.name = ValueString.parseValueString(action.getFunction().getApi(), yamlMap, "name", id);
        this.tags = Tag.parseTags(yamlMap, action);
    }

    public synchronized String getId() {
        return id;
    }

    public synchronized ValueString getDefPath() {
        return defPath;
    }

    public synchronized ValueString getItems() {
        return items;
    }

    public synchronized ValueString getPath() {
        return path;
    }

    public synchronized ValueString getName() {
        return name;
    }

    public synchronized List<Tag> getTags() {
        return tags;
    }
}
