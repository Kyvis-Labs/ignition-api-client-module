package com.kyvislabs.api.client.gateway.managers;

import com.inductiveautomation.ignition.common.resourcecollection.PushException;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.config.CollisionPolicy;
import com.inductiveautomation.ignition.gateway.config.DecodedResource;
import com.inductiveautomation.ignition.gateway.config.ModifiedResource;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.authentication.OAuth2;
import com.kyvislabs.api.client.gateway.api.authentication.OAuth2Servlet;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.StoreFileAction;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.StoreFileServlet;
import com.kyvislabs.api.client.gateway.api.webhooks.Webhook;
import com.kyvislabs.api.client.gateway.api.webhooks.WebhookServlet;
import com.kyvislabs.api.client.gateway.records.APIResource;
import com.kyvislabs.api.client.gateway.records.ResourceTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public class APIManager {
    private final Logger logger = LoggerFactory.getLogger("API.Manager");

    private static APIManager _INSTANCE = null;

    public static APIManager get() {
        if (_INSTANCE == null) {
            _INSTANCE = new APIManager();
        }
        return _INSTANCE;
    }

    private GatewayContext gatewayContext;
    private TagManager tagManager;
    private Map<String, API> apiConfigurations;
    private KeyStore keyStore;
    private APIResourceHandler resourceHandler;

    public APIManager() {
        tagManager = new TagManager();
        apiConfigurations = new ConcurrentHashMap<>();
    }

    public void setGatewayContext(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;

        try {
            keyStore = KeyStore.getInstance("pkcs12");
            Path path = Paths.get(gatewayContext.getSystemManager().getDataDir().toURI())
                    .getParent().resolve("webserver").resolve("ssl.pfx");
            File sslPfx = new File(path.toString());
            keyStore.load(new FileInputStream(sslPfx), "ignition".toCharArray());
        } catch (Throwable t) {
            logger.warn("Ignition is not set up for SSL: " + t.getMessage());
            keyStore = null;
        }

        tagManager.init(this.gatewayContext);
    }

    public void startup() throws Exception {
        logger.debug("Starting up");
        tagManager.startup();
        registerUDTs();

        gatewayContext.getWebResourceManager().addServlet(OAuth2.AUTH_TYPE, OAuth2Servlet.class);
        gatewayContext.getWebResourceManager().addServlet(Webhook.SERVLET_PATH, WebhookServlet.class);
        gatewayContext.getWebResourceManager().addServlet(StoreFileAction.SERVLET_PATH, StoreFileServlet.class);

        resourceHandler = new APIResourceHandler(gatewayContext, ResourceTypes.API_RESOURCE_TYPE_META);
        resourceHandler.startup();

        // Start all currently registered APIs
        for (DecodedResource<APIResource> resource : resourceHandler.getResources()) {
            apiAddAndStartup(resource.name(), resource.config());
        }
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

        if (resourceHandler != null) {
            try {
                resourceHandler.shutdown();
            } catch (Throwable ex) {
                logger.error("Error shutting down resource handler", ex);
            }
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

        apiConfigurations.clear();
    }

    private void apiAddAndStartup(String name, APIResource resource) {
        try {
            logger.debug("Starting up API '" + name + "'");
            API api = new API(this, name, resource);
            api.startup();
            apiConfigurations.put(name, api);
        } catch (Throwable ex) {
            logger.error("Error starting up " + name, ex);
        }
    }

    public String getAPIStatus(String name) {
        if (apiConfigurations.containsKey(name)) {
            return apiConfigurations.get(name).getStatusDisplay();
        }
        return "Unknown";
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

    public API getAPI(String name) throws APIException {
        if (apiConfigurations.containsKey(name)) {
            return apiConfigurations.get(name);
        }
        throw new APIException("API '" + name + "' doesn't exist");
    }

    /**
     * Persists a change to an API's resource (e.g. an updated variable or webhook key) back to the
     * ConfigurationManager, so it survives a gateway restart. This will asynchronously trigger
     * {@link APIResourceHandler#onResourceUpdated}, which reloads the affected API from the new resource.
     */
    public void updateResource(String name, UnaryOperator<APIResource> mutator) {
        try {
            resourceHandler.findResource(name).ifPresentOrElse(
                    existing -> {
                        APIResource updated = mutator.apply(existing.config());
                        try {
                            resourceHandler.modify(name, updated).exceptionally(ex -> {
                                logger.error("Error persisting updated resource for '" + name + "'", ex);
                                return null;
                            });
                        } catch (PushException ex) {
                            logger.error("Error persisting updated resource for '" + name + "'", ex);
                        }
                    },
                    () -> logger.warn("Cannot persist resource update for '" + name + "' - resource not found")
            );
        } catch (Throwable ex) {
            logger.error("Error persisting updated resource for '" + name + "'", ex);
        }
    }

    /**
     * Handles add/update/remove notifications from ConfigurationManager.
     */
    private class APIResourceHandler extends NamedResourceHandler<APIResource> {

        APIResourceHandler(GatewayContext gatewayContext, ResourceTypeMeta<APIResource> meta) {
            super(gatewayContext, meta);
        }

        @Override
        public boolean isRenameAware() {
            return true;
        }

        @Override
        public void onResourceAdded(DecodedResource<APIResource> resource) {
            logger.debug("API resource added: " + resource.name());
            apiAddAndStartup(resource.name(), resource.config());
        }

        @Override
        public void onResourceUpdated(ModifiedResource<APIResource> modified) {
            String name = modified.newResource().name();
            logger.debug("API resource updated: " + name);

            // Shutdown the old instance if it exists
            String oldName = modified.oldResource() != null ? modified.oldResource().name() : name;
            if (apiConfigurations.containsKey(oldName)) {
                try {
                    apiConfigurations.get(oldName).shutdown();
                    apiConfigurations.remove(oldName);
                } catch (Throwable ex) {
                    logger.error("Error shutting down old instance of " + oldName, ex);
                }
            }

            apiAddAndStartup(name, modified.newResource().config());
        }

        @Override
        public void onResourceRemoved(DecodedResource<APIResource> resource) {
            String name = resource.name();
            logger.debug("API resource removed: " + name);
            if (apiConfigurations.containsKey(name)) {
                try {
                    apiConfigurations.get(name).shutdown();
                    apiConfigurations.remove(name);
                } catch (Throwable ex) {
                    logger.error("Error shutting down " + name, ex);
                }
            }
        }
    }
}
