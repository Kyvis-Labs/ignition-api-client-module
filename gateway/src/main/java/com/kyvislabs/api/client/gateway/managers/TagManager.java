package com.kyvislabs.api.client.gateway.managers;

import com.inductiveautomation.ignition.common.config.BasicBoundPropertySet;
import com.inductiveautomation.ignition.common.config.BoundPropertySet;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.TagProviderMeta;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.config.CollisionPolicy;
import com.inductiveautomation.ignition.common.tags.config.TagConfiguration;
import com.inductiveautomation.ignition.common.tags.config.properties.WellKnownTagProps;
import com.inductiveautomation.ignition.common.tags.model.SecurityContext;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import com.inductiveautomation.ignition.gateway.tags.managed.ProviderConfiguration;
import com.inductiveautomation.ignition.gateway.tags.managed.WriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TagManager {
    private final Logger logger = LoggerFactory.getLogger("API.Tag.Manager");
    public static final String PROVIDER_NAME = "API";

    private GatewayContext gatewayContext;
    private ManagedTagProvider managedTagProvider;
    private TagProvider tagProvider;

    public void init(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
    }

    public void startup() {
        logger.debug("Starting up");
        managedTagProvider = gatewayContext.getTagManager().getOrCreateManagedProvider(new ProviderConfiguration(PROVIDER_NAME).setAllowTagCustomization(true).setPersistTags(true).setPersistValues(true).setAllowTagDeletion(true).setHasDataTypes(true).setAttribute(TagProviderMeta.FLAG_HAS_OPCBROWSE, false));
        tagProvider = gatewayContext.getTagManager().getTagProvider(PROVIDER_NAME);
    }

    public void shutdown() {
        logger.debug("Shutting down");
        managedTagProvider.shutdown(false);
    }

    public static String fixTagPath(String tagPath) {
        String tagProvider = String.format("[%s]", TagManager.PROVIDER_NAME);
        if (!tagPath.startsWith(tagProvider)) {
            tagPath = tagProvider + tagPath;
        }

        tagPath = sanitize(tagPath);

        return tagPath;
    }

    public static String sanitize(String tagPath) {
        tagPath = tagPath.replace(".", "_");
        return tagPath;
    }

    public boolean tagExists(String tagPath) {
        try {
            tagPath = fixTagPath(tagPath);
            QualifiedValue value = readTag(tagPath);
            logger.debug("Checking if tag exists '" + tagPath + "' with value '" + value.toString() + "'");
            if (value.getQuality().getCode() != QualityCode.Bad_NotFound.getCode()) {
                return true;
            }
        } catch (Throwable ex) {
            logger.error("Error finding tag", ex);
        }

        return false;
    }

    public boolean tagIsNull(String tagPath) {
        try {
            tagPath = fixTagPath(tagPath);
            QualifiedValue value = readTag(tagPath);
            logger.debug("Checking if tag is null '" + tagPath + "' with value '" + value.toString() + "'");
            if (value.getQuality().isGood() && value.getValue() == null) {
                return true;
            }
        } catch (Throwable ex) {
            logger.error("Error finding tag", ex);
        }

        return false;
    }

    public void registerUDT(TagConfiguration udt) throws Exception {
        registerUDTs(Arrays.asList(udt));
    }

    public void registerUDT(TagConfiguration udt, CollisionPolicy policy) throws Exception {
        registerUDTs(Arrays.asList(udt), policy);
    }

    public void registerUDTs(List<TagConfiguration> udts) throws Exception {
        registerUDTs(udts, CollisionPolicy.Ignore);
    }

    public void registerUDTs(List<TagConfiguration> udts, CollisionPolicy policy) throws Exception {
        List<TagConfiguration> finalUdts = new ArrayList<>();
        for (TagConfiguration udt : udts) {
            if (!tagExists(udt.getPath().toStringFull())) {
                finalUdts.add(udt);
                logger.debug("Registering UDT: " + udt.toString());
            }
        }
        if (finalUdts.size() > 0) {
            tagProvider.saveTagConfigsAsync(finalUdts, policy).get();
        }
    }

    public void configureTag(TagConfiguration tagConfiguration) {
        configureTag(tagConfiguration.getPath().toStringFull(), tagConfiguration.getTagProperties());
    }

    public void configureTag(String tagPath, DataType dataType) {
        configureTag(tagPath, dataType, null);
    }

    public void configureTag(String tagPath, DataType dataType, Object value) {
        BasicBoundPropertySet props = new BasicBoundPropertySet();
        props.set(WellKnownTagProps.DataType, dataType);
        if (value != null) {
            props.set(WellKnownTagProps.Value, new BasicQualifiedValue(value));
        }
        configureTag(tagPath, props);
    }

    public void configureTag(String tagPath, BoundPropertySet props) {
        tagPath = fixTagPath(tagPath);
        if (!tagExists(tagPath)) {
            logger.debug("Configuring tag '" + tagPath + "' with " + props.toString());
            managedTagProvider.configureTag(tagPath, props);
        }
    }

    public void registerWriteHandler(String tagPath, WriteHandler handler) {
        tagPath = fixTagPath(tagPath);
        logger.debug("Registering tag write handler '" + tagPath + "'");
        managedTagProvider.registerWriteHandler(tagPath, handler);
    }

    public void tagUpdate(String tagPath, Object value) {
        tagUpdate(tagPath, value, QualityCode.Good);
    }

    public void tagUpdate(String tagPath, Object value, QualityCode qualityCode) {
        tagPath = fixTagPath(tagPath);
        logger.debug("Updating tag '" + tagPath + "' to value '" + (value == null ? "null" : value.toString()) + "'");
        managedTagProvider.updateValue(tagPath, value, qualityCode);
    }

    public void removeTag(String tagPath) {
        tagPath = fixTagPath(tagPath);
        if (tagExists(tagPath)) {
            logger.debug("Removing tag '" + tagPath + "'");
            managedTagProvider.removeTag(tagPath);
        }
    }

    public QualifiedValue readTag(String tagPath) throws Exception {
        return readTags(Arrays.asList(tagPath)).get(0);
    }

    public List<QualifiedValue> readTags(List<String> tagPaths) throws Exception {
        List<TagPath> tps = new ArrayList<>();
        for (String tagPath : tagPaths) {
            tagPath = fixTagPath(tagPath);
            TagPath tp = TagPathParser.parse(tagPath);
            tps.add(tp);
        }
        logger.debug("Reading tags [" + tps.stream().map(e -> e.toStringFull()).collect(Collectors.joining(",")) + "]");
        return tagProvider.readAsync(tps, SecurityContext.systemContext()).get();
    }
}
