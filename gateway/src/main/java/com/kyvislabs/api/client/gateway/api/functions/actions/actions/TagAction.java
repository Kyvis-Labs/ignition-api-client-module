package com.kyvislabs.api.client.gateway.api.functions.actions.actions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.functions.actions.condition.Switch;
import com.kyvislabs.api.client.gateway.api.functions.actions.tag.RenameTag;
import com.kyvislabs.api.client.gateway.api.functions.actions.tag.Tag;
import com.kyvislabs.api.client.gateway.api.functions.actions.tag.TagActionProcessor;
import com.kyvislabs.api.client.gateway.api.functions.actions.tag.UDT;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TagAction extends Action {
    public static final String ACTION = "tag";

    private Logger logger;
    private ValueString path;
    private ValueString filter;
    private TagActionEnum tagAction;
    private Map<String, UDT> udts;
    private List<Tag> tags;
    private List<RenameTag> renameTags;
    private Switch switchCases;

    public TagAction(Function function) {
        super(function);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Action.Tag", function.getApi().getName(), function.getLoggerName()));
        this.udts = new ConcurrentHashMap<>();
        this.switchCases = new Switch(function);
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        super.parse(version, yamlMap);

        if (!yamlMap.containsKey("type")) {
            throw new APIException("Missing type");
        }

        this.path = ValueString.parseValueString(function.getApi(), yamlMap, "path", function.getApi().getName());
        this.filter = ValueString.parseValueString(function.getApi(), yamlMap, "filter");
        this.tagAction = TagActionEnum.valueOf(yamlMap.get("type").toString().toUpperCase());

        if (this.tagAction.equals(TagActionEnum.SWITCH) && !yamlMap.containsKey("cases")) {
            throw new APIException("Missing switch cases");
        }

        if (yamlMap.containsKey("udts")) {
            List udtsList = (List) yamlMap.get("udts");
            for (Object udtObj : udtsList) {
                Map udtMap = (Map) udtObj;
                UDT udt = new UDT(this);
                udt.parse(version, udtMap);
                udts.put(udt.getId(), udt);
            }
        }

        this.tags = Tag.parseTags(version, yamlMap, this);
        this.renameTags = RenameTag.parseRenameTags(version, yamlMap, this);
        this.switchCases.parse(version, yamlMap);
    }

    public synchronized ValueString getPath() {
        return path;
    }

    public synchronized ValueString getFilter() {
        return filter;
    }

    public synchronized TagActionEnum getTagAction() {
        return tagAction;
    }

    public synchronized Map<String, UDT> getUdts() {
        return udts;
    }

    public synchronized List<Tag> getTags() {
        return tags;
    }

    public synchronized List<RenameTag> getRenameTags() {
        return renameTags;
    }

    public synchronized Switch getSwitchCases() {
        return switchCases;
    }

    @Override
    public void handleResponse(VariableStore store, int statusCode, String contentType, String response) throws APIException {
        TagActionProcessor processor = new TagActionProcessor(logger, this);
        processor.process(store, response);
    }

    public enum TagActionEnum {
        JSONWRITE,
        JSONEXPAND,
        TEXT,
        SWITCH;
    }
}
