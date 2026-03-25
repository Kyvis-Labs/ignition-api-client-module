package com.kyvislabs.api.client.gateway.api.functions.actions.tag;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RenameTag implements YamlParser {
    private Action action;
    private ValueString items;
    private ValueString name;

    public RenameTag(Action action) {
        this.action = action;
    }

    public static List<RenameTag> parseRenameTags(Integer version, Map yamlMap, Action action) throws APIException {
        List<RenameTag> renameTags = Collections.synchronizedList(new ArrayList<>());
        if (yamlMap.containsKey("rename")) {
            List renameList = (List) yamlMap.get("rename");
            for (Object renameObj : renameList) {
                Map renameMap = (Map) renameObj;
                RenameTag renameTag = new RenameTag(action);
                renameTag.parse(version, renameMap);
                renameTags.add(renameTag);
            }
        }
        return renameTags;
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        if (!yamlMap.containsKey("name")) {
            throw new APIException("Rename tag missing name");
        }

        this.items = ValueString.parseItemsValueString(action.getFunction().getApi(), yamlMap);
        this.name = ValueString.parseValueString(action.getFunction().getApi(), yamlMap, "name", true);
    }

    public synchronized ValueString getItems() {
        return items;
    }

    public synchronized ValueString getName() {
        return name;
    }
}
