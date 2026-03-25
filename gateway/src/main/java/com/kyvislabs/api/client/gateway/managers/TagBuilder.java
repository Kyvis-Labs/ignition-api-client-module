package com.kyvislabs.api.client.gateway.managers;

import com.inductiveautomation.ignition.common.config.BasicBoundPropertySet;
import com.inductiveautomation.ignition.common.config.BoundPropertySet;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.config.BasicTagConfiguration;
import com.inductiveautomation.ignition.common.tags.config.TagConfiguration;
import com.inductiveautomation.ignition.common.tags.config.TagExecutionMode;
import com.inductiveautomation.ignition.common.tags.config.properties.WellKnownTagProps;
import com.inductiveautomation.ignition.common.tags.config.types.ExpressionTypeProperties;
import com.inductiveautomation.ignition.common.tags.config.types.ReferenceTagTypeProps;
import com.inductiveautomation.ignition.common.tags.config.types.TagObjectType;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;

import java.util.ArrayList;
import java.util.List;

public class TagBuilder {
    private BasicTagConfiguration tag;
    private List<String> children;

    public TagBuilder(BasicTagConfiguration tag) {
        this.tag = tag;
        children = new ArrayList<>();
    }

    public static final String getFullTagPath(String path, String name) throws Exception {
        path = TagManager.sanitize(path);
        TagPath tagPath = TagPathParser.parse(path);
        TagConfiguration tag = BasicTagConfiguration.createNew(tagPath);
        if (name != null) {
            tag.set(WellKnownTagProps.Name, name);
        }
        return tag.getPath().toStringFull();
    }

    public static final TagBuilder createUDTDefinition(String path) throws Exception {
        path = TagManager.sanitize(path);
        BoundPropertySet udtDef = new BasicBoundPropertySet();
        TagPath udtDefPath = TagPathParser.parse(String.format("[%s]_types_/%s", TagManager.PROVIDER_NAME, path));
        udtDef.set(WellKnownTagProps.Name, udtDefPath.getLastPathComponent());
        udtDef.set(WellKnownTagProps.TagType, TagObjectType.UdtType);
        return new TagBuilder(BasicTagConfiguration.createNew(udtDefPath, udtDef));
    }

    public static final TagBuilder createUDTInstance(String udtDefPath, String path) throws Exception {
        path = TagManager.sanitize(path);
        return new TagBuilder(createUDTMember(udtDefPath, path));
    }

    private static BasicTagConfiguration createUDTMember(String udtDefPath, String path) throws Exception {
        path = TagManager.sanitize(path);
        BoundPropertySet udt = new BasicBoundPropertySet();
        TagPath udtPath = TagPathParser.parse(String.format("[%s]%s", TagManager.PROVIDER_NAME, path));
        udt.set(WellKnownTagProps.Name, udtPath.getLastPathComponent());
        udt.set(WellKnownTagProps.TagType, TagObjectType.UdtInstance);
        udt.set(WellKnownTagProps.TypeId, udtDefPath);
        return BasicTagConfiguration.createNew(udtPath, udt);
    }

    private static BoundPropertySet getTagSet(TagPath tagPath, DataType dataType) throws Exception {
        BoundPropertySet tag = new BasicBoundPropertySet();
        tag.set(WellKnownTagProps.Name, tagPath.getLastPathComponent());
        tag.set(WellKnownTagProps.TagType, TagObjectType.AtomicTag);

        if (dataType == null) {
            dataType = DataType.String;
        }

        tag.set(WellKnownTagProps.DataType, dataType);
        if (dataType.equals(DataType.DateTime)) {
            tag.set(WellKnownTagProps.FormatString, "yyyy-MM-dd h:mm:ss aa");
        }
        return tag;
    }

    public TagBuilder addMember(String path, DataType dataType) throws Exception {
        return addMember(path, dataType, null);
    }

    public TagBuilder addMember(String path, DataType dataType, Object value) throws Exception {
        path = TagManager.sanitize(path);
        if (!children.contains(path)) {
            TagPath tagPath = TagPathParser.parse(path);
            BoundPropertySet tag = getTagSet(tagPath, dataType);
            if (value != null) {
                tag.set(WellKnownTagProps.Value, new BasicQualifiedValue(value));
            }
            BasicTagConfiguration cfg = BasicTagConfiguration.createNew(tagPath, tag);
            this.tag.addChild(cfg);
            children.add(path);
        }
        return this;
    }

    public static TagConfiguration createExpressionTag(String path, DataType dataType, String expression) throws Exception {
        path = TagManager.sanitize(path);
        TagPath tagPath = TagPathParser.parse(path);
        BoundPropertySet tag = getTagSet(tagPath, dataType);
        tag.set(WellKnownTagProps.ValueSource, ExpressionTypeProperties.TAG_TYPE);
        tag.set(ExpressionTypeProperties.Expression, expression);
        tag.set(ExpressionTypeProperties.ExecutionMode, TagExecutionMode.EventDriven);
        BasicTagConfiguration cfg = BasicTagConfiguration.createNew(tagPath, tag);
        return cfg;
    }

    public static TagConfiguration createDerivedTag(String path, DataType dataType, String sourceTagPath, String readExpression, String writeExpression) throws Exception {
        path = TagManager.sanitize(path);
        TagPath tagPath = TagPathParser.parse(path);
        BoundPropertySet tag = getTagSet(tagPath, dataType);
        tag.set(WellKnownTagProps.ValueSource, "derived");
        tag.set(ReferenceTagTypeProps.SourceTagPath, sourceTagPath);
        tag.set(ReferenceTagTypeProps.DeriveExpressionGetter, readExpression);
        tag.set(ReferenceTagTypeProps.DeriveExpressionSetter, writeExpression);
        BasicTagConfiguration cfg = BasicTagConfiguration.createNew(tagPath, tag);
        return cfg;
    }

    public TagBuilder addExpressionMember(String path, DataType dataType, String expression) throws Exception {
        path = TagManager.sanitize(path);
        if (!children.contains(path)) {
            this.tag.addChild(createExpressionTag(path, dataType, expression));
            children.add(path);
        }
        return this;
    }

    public TagBuilder addDerivedMember(String path, DataType dataType, String sourceTagPath, String readExpression, String writeExpression) throws Exception {
        path = TagManager.sanitize(path);
        sourceTagPath = TagManager.sanitize(sourceTagPath);
        if (!children.contains(path)) {
            this.tag.addChild(createDerivedTag(path, dataType, sourceTagPath, readExpression, writeExpression));
            children.add(path);
        }
        return this;
    }

    public TagBuilder addUDTMember(String udtDefPath, String path) throws Exception {
        path = TagManager.sanitize(path);
        if (!children.contains(path)) {
            tag.addChild(createUDTMember(udtDefPath, path));
            children.add(path);
        }
        return this;
    }

    public TagBuilder setName(String name) {
        if (name != null) {
            tag.set(WellKnownTagProps.Name, name);
        }
        return this;
    }

    public TagBuilder setValue(Object value) {
        if (value != null) {
            tag.set(WellKnownTagProps.Value, new BasicQualifiedValue(value));
        }
        return this;
    }

    public TagConfiguration build() {
        return tag;
    }
}
