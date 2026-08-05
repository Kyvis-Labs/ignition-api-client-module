package com.kyvislabs.api.client.gateway.managers;

import com.inductiveautomation.ignition.common.resourcecollection.PushException;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.config.CollisionPolicy;
import com.inductiveautomation.ignition.gateway.config.DecodedResource;
import com.inductiveautomation.ignition.gateway.config.ModifiedResource;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.PermissionType;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.secrets.Plaintext;
import com.inductiveautomation.ignition.gateway.secrets.SecretConfig;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.GatewayHook;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.Variables;
import com.kyvislabs.api.client.gateway.api.authentication.OAuth2;
import com.kyvislabs.api.client.gateway.api.authentication.OAuth2Servlet;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.StoreFileAction;
import com.kyvislabs.api.client.gateway.api.functions.actions.actions.StoreFileServlet;
import com.kyvislabs.api.client.gateway.api.webhooks.Webhook;
import com.kyvislabs.api.client.gateway.api.webhooks.WebhookServlet;
import com.kyvislabs.api.client.gateway.records.APIResource;
import com.kyvislabs.api.client.gateway.records.ResourceTypes;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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
     * Custom routes for actions on a running API instance (variables, certificate, OAuth2) that
     * aren't simple resource-field edits, and so don't go through the generic resource CRUD routes.
     * Mounted under /data/{mountPathAlias}/... - see GatewayHook.getMountPathAlias().
     */
    public void addRoutes(RouteGroup routes) {
        routes.newRoute("/api/v1/variables/:name").handler((requestContext, httpServletResponse) -> {
            try {
                API api = getAPI(requestContext.getParameter("name"));
                // Hidden variables (internal/auth-flow state - tokens, PKCE verifiers, etc.) are never
                // shown, matching the user-facing intent of "hidden" - neither editable nor read-only.
                List<Variables.VariableInfo> all = api.getVariables().getAllVariables().stream()
                        .filter(v -> !v.hidden())
                        .collect(Collectors.toList());
                List<Variables.VariableInfo> editable = all.stream()
                        .filter(Variables.VariableInfo::required)
                        .collect(Collectors.toList());
                List<Variables.VariableInfo> readOnly = all.stream()
                        .filter(v -> !v.required())
                        .collect(Collectors.toList());
                return new VariablesResponse(editable, readOnly);
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error getting variables", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.GET).requirePermission(PermissionType.READ).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/variables/:name").handler((requestContext, httpServletResponse) -> {
            try {
                API api = getAPI(requestContext.getParameter("name"));
                VariableUpdate[] updates = GatewayHook.GSON.fromJson(requestContext.readBody(), VariableUpdate[].class);
                for (VariableUpdate update : updates) {
                    api.getVariables().setVariable(update.key(), update.value());
                }
                return Map.of("success", true);
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error updating variables", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.POST).requirePermission(PermissionType.WRITE).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/certificate/:name").handler((requestContext, httpServletResponse) -> {
            try {
                API api = getAPI(requestContext.getParameter("name"));
                APIResource.APICertificate cert = api.getResource().certificate();
                return new CertificateInfo(
                        cert != null ? cert.certificate() : "",
                        cert != null && cert.privateKey() != null
                );
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error getting certificate", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.GET).requirePermission(PermissionType.READ).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/certificate/:name").handler((requestContext, httpServletResponse) -> {
            try {
                API api = getAPI(requestContext.getParameter("name"));
                CertificateUpdate update = GatewayHook.GSON.fromJson(requestContext.readBody(), CertificateUpdate.class);

                SecretConfig privateKey;
                if (update.privateKey() != null) {
                    try (Plaintext plaintext = Plaintext.fromString(update.privateKey())) {
                        privateKey = SecretConfig.embedded(api.getGatewayContext().getSystemEncryptionService().encryptToJson(plaintext));
                    }
                } else {
                    APIResource.APICertificate existing = api.getResource().certificate();
                    privateKey = existing != null ? existing.privateKey() : null;
                }

                APIResource.APICertificate newCert = new APIResource.APICertificate(update.certificate(), privateKey);
                api.persistResource(current -> new APIResource(
                        current.enabled(), current.configuration(), current.variables(), newCert, current.webhookKeys()
                ));
                return Map.of("success", true);
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error updating certificate", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.PUT).requirePermission(PermissionType.WRITE).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/oauth2/:name").handler((requestContext, httpServletResponse) -> {
            try {
                API api = getAPI(requestContext.getParameter("name"));
                if (!(api.getAuthType().getAuthType() instanceof OAuth2 authType)) {
                    return Map.of("enabled", false);
                }
                return new OAuth2Status(
                        true,
                        authType.getGrantType() != null ? authType.getGrantType().getType() : null,
                        authType.requiresPKCE(),
                        authType.requiresAuthCode(),
                        authType.requiresCaptcha(),
                        authType.requiresTwoFactor(),
                        authType.getGrantType() == OAuth2.GrantType.AUTHORIZATIONCODE ? authType.getAuthorizationUrl() : null,
                        authType.getGrantType() == OAuth2.GrantType.AUTHORIZATIONCODE ? authType.getActualRedirectUrl() : null
                );
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error getting OAuth2 status", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.GET).requirePermission(PermissionType.READ).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/oauth2/:name/authorize").handler((requestContext, httpServletResponse) -> {
            try {
                OAuth2 authType = getOAuth2(requestContext.getParameter("name"));
                byte[] captchaBytes = authType.getAuthorizationPage();
                return new OAuth2AuthorizeResult(captchaBytes != null ? Base64.getEncoder().encodeToString(captchaBytes) : null);
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error authorizing API", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.POST).requirePermission(PermissionType.WRITE).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/oauth2/:name/auth-code").handler((requestContext, httpServletResponse) -> {
            try {
                OAuth2 authType = getOAuth2(requestContext.getParameter("name"));
                CodeRequest body = GatewayHook.GSON.fromJson(requestContext.readBody(), CodeRequest.class);
                authType.setAuthorizationCode(body.code());
                return Map.of("success", true);
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error saving authorization code", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.POST).requirePermission(PermissionType.WRITE).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/oauth2/:name/captcha-code").handler((requestContext, httpServletResponse) -> {
            try {
                OAuth2 authType = getOAuth2(requestContext.getParameter("name"));
                CodeRequest body = GatewayHook.GSON.fromJson(requestContext.readBody(), CodeRequest.class);
                authType.setCaptchaCode(body.code());
                return Map.of("success", true);
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error saving captcha code", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.POST).requirePermission(PermissionType.WRITE).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/oauth2/:name/2fa-code").handler((requestContext, httpServletResponse) -> {
            try {
                API api = getAPI(requestContext.getParameter("name"));
                CodeRequest body = GatewayHook.GSON.fromJson(requestContext.readBody(), CodeRequest.class);
                api.getVariables().setVariable(OAuth2.VARIABLE_2FA_CODE, body.code());
                api.getVariables().clearVariable(OAuth2.VARIABLE_2FA_CODE_WAITING);
                return Map.of("success", true);
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error saving 2FA code", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.POST).requirePermission(PermissionType.WRITE).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();

        routes.newRoute("/api/v1/oauth2/:name/2fa-reset").handler((requestContext, httpServletResponse) -> {
            try {
                API api = getAPI(requestContext.getParameter("name"));
                api.getVariables().clearVariable(OAuth2.VARIABLE_2FA_CODE);
                api.getVariables().clearVariable(OAuth2.VARIABLE_2FA_CODE_WAITING);
                return Map.of("success", true);
            } catch (APIException ex) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
                return null;
            } catch (Throwable t) {
                logger.error("Error resetting 2FA", t);
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
                return null;
            }
        }).method(HttpMethod.POST).requirePermission(PermissionType.WRITE).type(RouteGroup.TYPE_JSON).renderer(GatewayHook.GSON::toJson).mount();
    }

    private OAuth2 getOAuth2(String apiName) throws APIException {
        API api = getAPI(apiName);
        if (api.getAuthType().getAuthType() instanceof OAuth2 authType) {
            return authType;
        }
        throw new APIException("API '" + apiName + "' is not configured for OAuth2");
    }

    private record VariablesResponse(List<Variables.VariableInfo> editable, List<Variables.VariableInfo> readOnly) {}
    private record VariableUpdate(String key, String value) {}
    private record CertificateInfo(String certificate, boolean hasPrivateKey) {}
    private record CertificateUpdate(String certificate, String privateKey) {}
    private record OAuth2Status(boolean enabled, String grantType, boolean requiresPKCE, boolean requiresAuthCode,
                                 boolean requiresCaptcha, boolean requiresTwoFactor, String authorizationUrl, String redirectUrl) {}
    private record OAuth2AuthorizeResult(String captchaImageBase64) {}
    private record CodeRequest(String code) {}

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
                    API api = apiConfigurations.get(name);
                    api.markDeleted();
                    api.shutdown();
                    apiConfigurations.remove(name);
                } catch (Throwable ex) {
                    logger.error("Error shutting down " + name, ex);
                }
            }
        }
    }
}
