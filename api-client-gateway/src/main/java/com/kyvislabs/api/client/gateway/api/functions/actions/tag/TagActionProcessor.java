package com.kyvislabs.api.client.gateway.api.functions.actions.tag;

import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.TagAction;
import com.kyvislabs.api.client.gateway.api.functions.actions.condition.Case;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.managers.TagBuilder;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class TagActionProcessor {

    private Logger logger;
    private TagAction action;
    private VariableStore store;
    private String response;
    private Map<String, RenameValue> renamePathList = new HashMap<>();
    private Map<String, TagUDT> udtPathList = new HashMap<>();
    private List<ConfiguredTag> configuredTags = new ArrayList<>();
    private Map<String, TagBuilder> udtDefs = new LinkedHashMap<>();
    private List<TagBuilder> udtInst = new ArrayList<>();

    public TagActionProcessor(Logger logger, TagAction action) {
        this.logger = logger;
        this.action = action;
    }

    public void process(VariableStore store, String response) throws APIException {
        try {
            this.store = store;
            this.response = response;

            String prefix = action.getPath().getValue(store, this.response);
            String parentPath = prefix;
            if (parentPath != null && !parentPath.equals("")) {
                parentPath = parentPath + (parentPath.endsWith("/") ? "" : "/");
            } else {
                parentPath = "";
            }

            logger.debug("Processing tags at " + prefix);

            if (action.getFilter() != null) {
                try {
                    Object filter = action.getFilter().getValueAsObject(store, this.response);
                    if (filter instanceof Map) {
                        JSONObject obj = new JSONObject((Map) filter);
                        this.response = obj.toString();
                    } else {
                        this.response = filter.toString();
                    }

                    logger.debug("Filtered response  [response=" + response + "]");
                } catch (Throwable ex) {
                    logger.debug("Skipping response, error with filter", ex);
                    return;
                }
            }

            if (action.getTagAction().equals(TagAction.TagActionEnum.TEXT)) {
                String tagPath = parentPath + "Value";
                action.getFunction().getApi().getTagManager().tagUpdate(tagPath, this.response);
            } else if (action.getTagAction().equals(TagAction.TagActionEnum.SWITCH)) {
                Case switchCase = action.getSwitchCases().handleResponse(store, response);
                if (switchCase != null) {
                    String tagPath = parentPath + switchCase.getVariable("path").getValue(store, response);
                    action.getFunction().getApi().getTagManager().tagUpdate(tagPath, switchCase.getVariable("value").getValue(store, response));
                }
            } else if (action.getTagAction().equals(TagAction.TagActionEnum.JSONEXPAND) || action.getTagAction().equals(TagAction.TagActionEnum.JSONWRITE)) {
                // Find all the rename paths if they exist
                findRenames();

                // Find all the UDTs paths if they exist
                findUDTs();

                Object responseObj = this.response;

                try {
                    responseObj = new JSONObject(this.response);
                } catch (Exception ex) {

                }

                try {
                    responseObj = new JSONArray(this.response);
                } catch (Exception ex) {

                }

                // Build a nested set of objects that follows the JSON structure. If JSONEXPAND bring back everything. If JSONWRITE just bring back UDTs.
                List<ParseObject> parseObjects = iterateJSONObject(prefix, "$", responseObj, action.getTagAction().equals(TagAction.TagActionEnum.JSONEXPAND));

                // If we find UDTs, create and build the UDTs. The functions build a list of configured UDT instances so we can create/update tags from the YAML and issue write handlers
                if (udtPathList.size() > 0) {
                    // Finds all UDTs by finding the leaf UDTs first. We need to figure out the hierarchy to know what definitions we need to create since we can have composition
                    createUDTs(parseObjects, false);

                    // Creates the UDT definition and instances. First UDT defs than instances.
                    buildUDTs();
                }

                // Writes all of the response values to the tags
                updateTags(parseObjects);

                // Register configured tags from UDTs
                registerUDTTags();

                // Register standard configured tags
                registerTags(parentPath, action.getTags(), true);
            }
        } catch (Throwable ex) {
            throw new APIException("Error handling tag action", ex);
        }
    }

    private void findRenames() throws APIException {
        for (RenameTag renameTag : action.getRenameTags()) {
            for (String item : renameTag.getItems().getValues(store, response)) {
                try {
                    renamePathList.put(item, new RenameValue(renameTag.getName().getValue(store, response, item)));
                } catch (Throwable ex) {
                    logger.debug("Error getting rename name value", ex);
                }
            }
        }

        logger.debug("Found Renames: " + renamePathList.keySet().stream()
                .map(key -> key + "=" + renamePathList.get(key))
                .collect(Collectors.joining(", ", "{", "}")));
    }

    private void findUDTs() throws APIException {
        for (UDT udt : action.getUdts().values()) {
            for (String item : udt.getItems().getValues(store, response)) {
                udtPathList.put(item, new TagUDT(udt.getId(), udt.getPath().getValue(store, response, item), udt.getName().getValue(store, response, item)));
            }
        }

        logger.debug("Found UDTs: " + udtPathList.keySet().stream()
                .map(key -> key + "=" + udtPathList.get(key).getUdt())
                .collect(Collectors.joining(", ", "{", "}")));
    }

    private List<ParseObject> iterateJSONObject(String prefix, String jsonPath, Object input, boolean jsonExpand) throws Exception {
        List<ParseObject> parseObjects = new ArrayList<>();

        String parentPath = prefix;
        if (parentPath != null && !parentPath.equals("")) {
            parentPath = parentPath + (parentPath.endsWith("/") ? "" : "/");
        } else {
            parentPath = "";
        }

        if (jsonPath.equals("$") && udtPathList.containsKey(jsonPath)) {
            TagUDT udt = udtPathList.getOrDefault(jsonPath, new TagUDT());
            List<ParseObject> childrenParseObjects = new ArrayList<>();
            String newPrefix = String.format("%s%s", parentPath, udt.hasName() ? udt.getName() : renamePathList.getOrDefault(jsonPath, new RenameValue("value")).getName());
            if (renamePathList.containsKey(jsonPath)) {
                String origPrefix = String.format("%s%s", parentPath, udt.hasName() ? udt.getName() : "value");
                renamePathList.get(jsonPath).setTagPaths(origPrefix, newPrefix);
            }
            parseObjects.add(new ParseObject(newPrefix, jsonPath, udt.getUdt(), childrenParseObjects));
            parseObjects = childrenParseObjects;
        }

        if (input instanceof JSONObject) {
            Iterator<?> keys = ((JSONObject) input).keys();

            while (keys.hasNext()) {
                String key = (String) keys.next();
                Object obj = null;
                if (!((JSONObject) input).isNull(key)) {
                    obj = ((JSONObject) input).get(key);
                }
                String newJsonPath = String.format("%s['%s']", jsonPath, key);
                TagUDT udt = udtPathList.getOrDefault(newJsonPath, new TagUDT());
                String newPrefix = String.format("%s%s", parentPath, udt.hasName() ? udt.getName() : renamePathList.getOrDefault(newJsonPath, new RenameValue(key)).getName());
                if (renamePathList.containsKey(newJsonPath)) {
                    String origPrefix = String.format("%s%s", parentPath, udt.hasName() ? udt.getName() : key);
                    renamePathList.get(newJsonPath).setTagPaths(origPrefix, newPrefix);
                }
                handleJSONObject(parseObjects, obj, newPrefix, newJsonPath, udt, jsonExpand || udt.isUDT());
            }
        } else if (input instanceof JSONArray) {
            for (int i = 0; i < ((JSONArray) input).length(); i++) {
                Object obj = null;
                if (!((JSONArray) input).isNull(i)) {
                    obj = ((JSONArray) input).get(i);
                }
                String newJsonPath = String.format("%s[%d]", jsonPath, i);
                TagUDT udt = udtPathList.getOrDefault(newJsonPath, new TagUDT());
                String newPrefix = String.format("%s%s", parentPath, udt.hasName() ? udt.getName() : renamePathList.getOrDefault(newJsonPath, new RenameValue(Integer.toString(i))).getName());
                if (renamePathList.containsKey(newJsonPath)) {
                    String origPrefix = String.format("%s%s", parentPath, udt.hasName() ? udt.getName() : Integer.toString(i));
                    renamePathList.get(newJsonPath).setTagPaths(origPrefix, newPrefix);
                }
                handleJSONObject(parseObjects, obj, newPrefix, newJsonPath, udt, jsonExpand || udt.isUDT());
            }
        } else {
            if (jsonExpand) {
                String newPrefix = String.format("%s%s", parentPath, renamePathList.getOrDefault(jsonPath, new RenameValue("value")).getName());
                if (renamePathList.containsKey(jsonPath)) {
                    String origPrefix = String.format("%s%s", parentPath, "value");
                    renamePathList.get(jsonPath).setTagPaths(origPrefix, newPrefix);
                }
                parseObjects.add(new ParseObject(newPrefix, null, input));
            }
        }

        return parseObjects;
    }

    private void handleJSONObject(List<ParseObject> parseObjects, Object obj, String newPrefix, String newJsonPath, TagUDT udt, boolean jsonExpand) throws Exception {
        if (obj instanceof JSONObject || obj instanceof JSONArray) {
            parseObjects.add(new ParseObject(newPrefix, newJsonPath, udt.getUdt(), iterateJSONObject(newPrefix, newJsonPath, obj, jsonExpand || udt.isUDT())));
        } else if (jsonExpand) {
            parseObjects.add(new ParseObject(newPrefix, newJsonPath, obj));
        }
    }

    private void createUDTs(List<ParseObject> parseObjects, boolean parentUdt) throws Exception {
        for (ParseObject tag : parseObjects) {
            if (tag.hasChildren()) {
                createUDTs(tag.getChildren(), parentUdt || tag.isUdt());
            }

            if (tag.isUdt()) {
                if (action.getUdts().containsKey(tag.getUdt())) {
                    UDT udt = action.getUdts().get(tag.getUdt());
                    String udtDefPath = udt.getDefPath().getValue(store, response);

                    if (!udtDefs.containsKey(tag.getUdt())) {
                        udtDefs.put(tag.getUdt(), TagBuilder.createUDTDefinition(udtDefPath));
                    }

                    TagBuilder builder = udtDefs.get(tag.getUdt());
                    processUDTChildren(builder, tag.getTagPath() + "/", tag.getChildren());
                    processUDTTags(builder, udt);
                    configuredTags.add(new ConfiguredTag(udt, tag.getTagPath()));

                    if (!parentUdt) {
                        udtInst.add(TagBuilder.createUDTInstance(udtDefPath, tag.getTagPath()));
                    }
                }
            }
        }
    }

    private void processUDTChildren(TagBuilder builder, String removePath, List<ParseObject> children) throws Exception {
        for (ParseObject child : children) {
            if (child.isUdt()) {
                if (action.getUdts().containsKey(child.getUdt())) {
                    UDT udt = action.getUdts().get(child.getUdt());
                    String udtDefPath = udt.getDefPath().getValue(store, response);
                    builder.addUDTMember(udtDefPath, child.getTagPath().replace(removePath, ""));
                    configuredTags.add(new ConfiguredTag(udt, child.getTagPath()));
                }
            } else if (child.hasChildren()) {
                processUDTChildren(builder, removePath, child.getChildren());
            } else if (child.hasValue()) {
                builder.addMember(child.getTagPath().replace(removePath, ""), DataType.getTypeForClass(child.getValue().getClass()));
            }
        }
    }

    private void processUDTTags(TagBuilder builder, UDT udt) throws Exception {
        for (Tag tag : udt.getTags()) {
            for (String item : tag.getItems().getValues(store, response)) {
                String tagPath = tag.getTagPath(store, response, item);

                if (renamePathList.containsKey(item)) {
                    tagPath = tagPath.replace(renamePathList.get(item).getOrigTagPath(), renamePathList.get(item).getNewTagPath());
                }

                if (tag.isExpression()) {
                    builder.addExpressionMember(tagPath, tag.getDataType(), tag.getExpression());
                } else if (tag.isDerived()) {
                    builder.addDerivedMember(tagPath, tag.getDataType(), tag.getDerivedSource(), tag.getDerivedRead(), tag.getDerivedWrite());
                } else {
                    builder.addMember(tagPath, tag.getDataType(), tag.getDefaultValue() == null ? null : tag.getDefaultValue().getValue(store, response, item));
                }
            }
        }
    }

    private void buildUDTs() throws Exception {
        for (TagBuilder builder : udtDefs.values()) {
            action.getFunction().getApi().getTagManager().registerUDT(builder.build());
        }

        for (TagBuilder builder : udtInst) {
            action.getFunction().getApi().getTagManager().registerUDT(builder.build());
        }
    }

    private void updateTags(List<ParseObject> parseObjects) {
        for (ParseObject tag : parseObjects) {
            if (tag.hasValue()) {
                action.getFunction().getApi().getTagManager().tagUpdate(tag.getTagPath(), tag.getValue());
            }

            if (tag.hasChildren()) {
                updateTags(tag.getChildren());
            }
        }
    }

    private void registerUDTTags() throws Exception {
        for (ConfiguredTag configuredTag : configuredTags) {
            registerTags(configuredTag.getParentPath(), configuredTag.getUdt().getTags(), false);
        }
    }

    private void registerTags(String parentPath, List<Tag> tags, boolean configure) throws Exception {
        for (Tag tag : tags) {
            for (String item : tag.getItems().getValues(store, response)) {
                String tagPath = tag.getTagPath(store, response, item);
                registerTag(parentPath, tagPath, tag, item, configure);
            }
        }
    }

    private void registerTag(String parentPath, String tagPath, Tag tag, String item, boolean configure) throws Exception {
        if (parentPath == null) {
            parentPath = "";
        }

        if (parentPath.endsWith("/")) {
            parentPath = StringUtils.chop(parentPath);
        }

        tagPath = parentPath + "/" + tagPath;

        if (renamePathList.containsKey(item)) {
            tagPath = tagPath.replace(renamePathList.get(item).getOrigTagPath(), renamePathList.get(item).getNewTagPath());
        }

        if (tag.getAddIfNotExists() || action.getFunction().getApi().getTagManager().tagExists(tagPath)) {
            if (configure && tag.getAddIfNotExists()) {
                if (tag.isExpression()) {
                    action.getFunction().getApi().getTagManager().registerTag(TagBuilder.createExpressionTag(tagPath, tag.getDataType(), tag.getExpression()));
                } else if (tag.isDerived()) {
                    action.getFunction().getApi().getTagManager().registerTag(TagBuilder.createDerivedTag(tagPath, tag.getDataType(), tag.getDerivedSource(), tag.getDerivedRead(), tag.getDerivedWrite()));
                } else {
                    action.getFunction().getApi().getTagManager().configureTag(tagPath, tag.getDataType());
                }
            }

            if (tag.hasHandler()) {
                action.getFunction().getApi().getTagManager().registerWriteHandler(tagPath, tag.getHandler().getWriteHandler(parentPath, tag));
            }

            if ((tag.getDefaultValue() != null && action.getFunction().getApi().getTagManager().tagIsNull(tagPath)) || tag.getValue() != null) {
                Object value = null;

                if (tag.getDefaultValue() != null) {
                    value = tag.getDefaultValue().getValue(store, response, item);
                } else {
                    value = tag.getValue().getValue(store, response, item);
                }

                if (value != null) {
                    action.getFunction().getApi().getTagManager().tagUpdate(tagPath, value);
                }
            }
        }
    }

    private class TagUDT {
        private String udt;
        private String path;
        private String name;

        public TagUDT() {
            this(null, null, null);
        }

        public TagUDT(String udt, String path, String name) {
            this.udt = udt;
            this.path = path;
            this.name = name;
        }

        public String getUdt() {
            return udt;
        }

        public String getPath() {
            return path;
        }

        public String getName() {
            return name;
        }

        public boolean hasName() {
            return name != null && !name.equals("");
        }

        public boolean isUDT() {
            return udt != null && !udt.equals("");
        }
    }

    private class ConfiguredTag {
        private UDT udt;
        private String parentPath;

        public ConfiguredTag(UDT udt, String parentPath) {
            this.udt = udt;
            this.parentPath = parentPath;
        }

        public UDT getUdt() {
            return udt;
        }

        public String getParentPath() {
            return parentPath;
        }
    }

    private class ParseObject {
        private String tagPath;
        private String jsonPath;
        private Object value;
        private String udt;
        private List<ParseObject> children;

        public ParseObject(String tagPath, String jsonPath, Object value) {
            this(tagPath, jsonPath, value, null, null);
        }

        public ParseObject(String tagPath, String jsonPath, String udt, List<ParseObject> children) {
            this(tagPath, jsonPath, null, udt, children);
        }

        public ParseObject(String tagPath, String jsonPath, Object value, String udt, List<ParseObject> children) {
            this.tagPath = tagPath;
            this.jsonPath = jsonPath;
            this.value = value;
            this.udt = udt;
            this.children = children;
        }

        public String getTagPath() {
            return tagPath;
        }

        public String getJsonPath() {
            return jsonPath;
        }

        public Object getValue() {
            return value;
        }

        public String getUdt() {
            return udt;
        }

        public boolean hasValue() {
            return value != null;
        }

        public boolean isUdt() {
            return udt != null;
        }

        public List<ParseObject> getChildren() {
            return children;
        }

        public boolean hasChildren() {
            if (children != null && children.size() > 0) {
                return true;
            }

            return false;
        }
    }

    private class RenameValue {
        private String origTagPath, newTagPath;
        private String name;

        public RenameValue(String name) {
            this.name = name;
        }

        public String getOrigTagPath() {
            return origTagPath;
        }

        public String getNewTagPath() {
            return newTagPath;
        }

        public void setTagPaths(String origTagPath, String newTagPath) {
            this.origTagPath = origTagPath;
            this.newTagPath = newTagPath;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
