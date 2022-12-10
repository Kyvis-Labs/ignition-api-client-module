package com.kyvislabs.api.client.gateway.api.functions.actions.tag;

import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.api.interfaces.YamlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Tag implements YamlParser {
    private Action action;
    private ValueString items;
    private ValueString path;
    private ValueString name;
    private DataType dataType;
    private ValueString defaultValue;
    private ValueString value;
    private Handler handler;
    private String expression;
    private String derivedSource;
    private String derivedRead;
    private String derivedWrite;
    private Boolean addIfNotExists;

    public Tag(Action action) {
        this.action = action;
    }

    public static List<Tag> parseTags(Map yamlMap, Action action) throws APIException {
        List<Tag> tags = Collections.synchronizedList(new ArrayList<>());
        if (yamlMap.containsKey("tags")) {
            List tagsList = (List) yamlMap.get("tags");
            for (Object tagObj : tagsList) {
                Map tagMap = (Map) tagObj;
                Tag tag = new Tag(action);
                tag.parse(tagMap);
                tags.add(tag);
            }
        }
        return tags;
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        if (!yamlMap.containsKey("name")) {
            throw new APIException("Tag missing name");
        }

        if (!yamlMap.containsKey("dataType")) {
            throw new APIException("Tag missing data type");
        }

        this.items = ValueString.parseItemsValueString(action.getFunction().getApi(), yamlMap);
        this.path = ValueString.parseValueString(action.getFunction().getApi(), yamlMap, "path", "");
        this.name = ValueString.parseValueString(action.getFunction().getApi(), yamlMap, "name", true);
        this.dataType = DataType.valueOf((String) yamlMap.get("dataType"));
        this.defaultValue = ValueString.parseValueString(action.getFunction().getApi(), yamlMap, "defaultValue");
        this.value = ValueString.parseValueString(action.getFunction().getApi(), yamlMap, "value");
        this.expression = (String) yamlMap.getOrDefault("expression", null);

        if (yamlMap.containsKey("derived")) {
            Map derivedMap = (Map) yamlMap.get("derived");

            if (!derivedMap.containsKey("source")) {
                throw new APIException("Derived tag missing source");
            }

            if (!derivedMap.containsKey("read")) {
                throw new APIException("Derived tag missing read expression");
            }

            if (!derivedMap.containsKey("write")) {
                throw new APIException("Derived tag missing write expression");
            }

            this.derivedSource = (String) derivedMap.getOrDefault("source", null);
            this.derivedRead = (String) derivedMap.getOrDefault("read", null);
            this.derivedWrite = (String) derivedMap.getOrDefault("write", null);
        }

        if (yamlMap.containsKey("handler")) {
            Map handlerMap = (Map) yamlMap.get("handler");
            this.handler = new Handler(action);
            this.handler.parse(handlerMap);
        }

        addIfNotExists = Boolean.valueOf(yamlMap.getOrDefault("addIfNotExists", "true").toString());
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

    public synchronized String getTagPath(VariableStore store, String response, String item) throws APIException {
        StringBuilder builder = new StringBuilder();
        String path = getPath().getValue(store, response, item);
        builder.append(path);
        if (!path.equals("")) {
            builder.append("/");
        }
        builder.append(getName().getValue(store, response, item));
        return builder.toString();
    }

    public synchronized DataType getDataType() {
        return dataType;
    }

    public synchronized boolean hasHandler() {
        return handler != null;
    }

    public synchronized Handler getHandler() {
        return handler;
    }

    public boolean isExpression() {
        return getExpression() != null;
    }

    public boolean isDerived() {
        return getDerivedSource() != null && getDerivedRead() != null && getDerivedWrite() != null;
    }

    public synchronized String getExpression() {
        return expression;
    }

    public synchronized String getDerivedSource() {
        return derivedSource;
    }

    public synchronized String getDerivedRead() {
        return derivedRead;
    }

    public synchronized String getDerivedWrite() {
        return derivedWrite;
    }

    public synchronized ValueString getDefaultValue() {
        return defaultValue;
    }

    public synchronized ValueString getValue() {
        return value;
    }

    public synchronized Boolean getAddIfNotExists() {
        return addIfNotExists;
    }
}
