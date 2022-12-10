package com.kyvislabs.api.client.gateway.managers;

import com.inductiveautomation.ignition.common.licensing.LicenseMode;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.config.CollisionPolicy;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IRecordListener;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.web.models.KeyValue;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.authentication.OAuth2;
import com.kyvislabs.api.client.gateway.api.authentication.OAuth2Servlet;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.StoreFileAction;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.StoreFileServlet;
import com.kyvislabs.api.client.gateway.api.webhooks.Webhook;
import com.kyvislabs.api.client.gateway.api.webhooks.WebhookServlet;
import com.kyvislabs.api.client.gateway.database.APIRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class APIManager implements IRecordListener<APIRecord> {
    private final Logger logger = LoggerFactory.getLogger("API.Manager");

    private static APIManager _INSTANCE = null;
    private LicenseState licenseState;

    public static APIManager get() {
        if (_INSTANCE == null) {
            _INSTANCE = new APIManager();
        }
        return _INSTANCE;
    }

    private GatewayContext gatewayContext;
    private TagManager tagManager;
    private Map<Long, String> apiIdMap;
    private Map<String, API> apiConfigurations;
    private KeyStore keyStore;

    public APIManager() {
        tagManager = new TagManager();
        apiIdMap = new ConcurrentHashMap<>();
        apiConfigurations = new ConcurrentHashMap<>();
    }

    public void setGatewayContext(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;

        try {
            keyStore = KeyStore.getInstance("pkcs12");
            Path path = Paths.get(gatewayContext.getSystemManager().getDataDir().toURI()).getParent().resolve("webserver").resolve("ssl.pfx");
            File sslPfx = new File(path.toString());
            keyStore.load(new FileInputStream(sslPfx), "ignition".toCharArray());
        } catch (Throwable t) {
            logger.warn("Ignition is not set up for SSL", t);
            keyStore = null;
        }

        tagManager.init(this.gatewayContext);
    }

    public void startup() throws Exception {
        logger.debug("Starting up");
        APIRecord.META.addRecordListener(this);
        gatewayContext.getWebResourceManager().addServlet(OAuth2.AUTH_TYPE, OAuth2Servlet.class);
        gatewayContext.getWebResourceManager().addServlet(Webhook.SERVLET_PATH, WebhookServlet.class);
        gatewayContext.getWebResourceManager().addServlet(StoreFileAction.SERVLET_PATH, StoreFileServlet.class);
        tagManager.startup();
        registerUDTs();
        init();
    }

    private void registerUDTs() throws Exception {
        TagBuilder builder = TagBuilder.createUDTDefinition("Functions/Status");
        builder.addMember("ResponseCode", DataType.Int4);
        builder.addMember("LastExecution", DataType.DateTime);
        builder.addMember("NextExecution", DataType.DateTime);
        builder.addMember("Response", DataType.String);
        builder.addMember("Schedule", DataType.String);
        builder.addMember("State", DataType.String);
        builder.addMember("Status", DataType.String);
        builder.addMember("LastExecutionDuration", DataType.Int8);
        builder.addMember("LastExecutionSetupDuration", DataType.Int8);
        builder.addMember("LastExecutionCallDuration", DataType.Int8);
        builder.addMember("LastExecutionProcessDuration", DataType.Int8);
        tagManager.registerUDT(builder.build(), CollisionPolicy.Ignore);
    }

    public void shutdown() {
        logger.debug("Shutting down");

        try {
            APIRecord.META.removeRecordListener(this);
        } catch (Throwable ex) {
            logger.error("Error removing record listener", ex);
        }

        try {
            gatewayContext.getWebResourceManager().removeServlet(OAuth2.AUTH_TYPE);
        } catch (Throwable ex) {
            logger.error("Error removing OAuth2 servlet", ex);
        }

        try {
            gatewayContext.getWebResourceManager().removeServlet(Webhook.SERVLET_PATH);
        } catch (Throwable ex) {
            logger.error("Error removing webhook servlet", ex);
        }

        try {
            gatewayContext.getWebResourceManager().removeServlet(StoreFileAction.SERVLET_PATH);
        } catch (Throwable ex) {
            logger.error("Error removing image servlet", ex);
        }

        for (API api : apiConfigurations.values()) {
            try {
                api.shutdown();
            } catch (Throwable ex) {
                logger.error("Error shutting down " + api.getName(), ex);
            }
        }

        try {
            tagManager.shutdown();
        } catch (Throwable ex) {
            logger.error("Error shutting down tag manager", ex);
        }

        apiIdMap.clear();
        apiConfigurations.clear();
    }

    public void setLicenseState(LicenseState licenseState) {
        this.licenseState = licenseState;
    }

    public boolean isLicensed() {
        return licenseState.getLicenseMode() == LicenseMode.Activated;
    }

    public boolean isTrialExpired() {
        return licenseState.getLicenseMode() == LicenseMode.Trial && licenseState.isTrialExpired();
    }

    private void init() {
        SQuery<APIRecord> query = new SQuery<>(APIRecord.META);
        List<APIRecord> apis = gatewayContext.getPersistenceInterface().query(query);
        for (APIRecord api : apis) {
            apiAddAndStartup(api);
        }
    }

    private void apiAddAndStartup(APIRecord apiRecord) {
        try {
            logger.debug("Starting up API '" + apiRecord.getName() + "'");
            API api = new API(this, apiRecord);
            api.startup();
            apiIdMap.put(apiRecord.getId(), apiRecord.getName());
            apiConfigurations.put(apiRecord.getName(), api);
        } catch (Throwable ex) {
            logger.error("Error starting up " + apiRecord.getName(), ex);
        }
    }

    @Override
    public void recordUpdated(APIRecord record) {
        logger.debug("API " + record.getName() + "' record updated");

        long id = record.getId();
        String oldName = null;

        if (apiIdMap.containsKey(id)) {
            oldName = apiIdMap.get(id);
            apiIdMap.remove(id);
        }

        if (oldName != null && apiConfigurations.containsKey(oldName)) {
            try {
                API api = apiConfigurations.get(oldName);
                api.shutdown();
                apiConfigurations.remove(oldName);
            } catch (Throwable ex) {
                logger.error("Error shutting down old instance", ex);
            }
        }

        apiAddAndStartup(record);
    }

    @Override
    public void recordAdded(APIRecord record) {
        logger.debug("API " + record.getName() + "' record added");
        apiAddAndStartup(record);
    }

    @Override
    public void recordDeleted(KeyValue keyValue) {
        long id = (long) keyValue.getKeyValues()[0];
        logger.debug("API " + id + "' record deleted");

        if (apiIdMap.containsKey(id)) {
            try {
                API api = apiConfigurations.get(apiIdMap.get(id));
                api.shutdown();
            } catch (Throwable ex) {
                logger.error("Error shutting down old instance", ex);
            }
        }
    }

    public String getAPIStatus(String name) {
        String status = "Unknown";
        if (apiConfigurations.containsKey(name)) {
            status = apiConfigurations.get(name).getStatusDisplay();
        }
        return status;
    }

    public GatewayContext getGatewayContext() {
        return gatewayContext;
    }

    public TagManager getTagManager() {
        return tagManager;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public API getAPI(long id) throws APIException {
        if (apiIdMap.containsKey(id)) {
            return apiConfigurations.get(apiIdMap.get(id));
        }

        throw new APIException("API with id '" + id + "' doesn't exist");
    }

    public API getAPI(String name) throws APIException {
        if (apiConfigurations.containsKey(name)) {
            return apiConfigurations.get(name);
        }

        throw new APIException("API '" + name + "' doesn't exist");
    }
}
