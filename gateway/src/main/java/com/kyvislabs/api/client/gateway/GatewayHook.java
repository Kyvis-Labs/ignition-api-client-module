package com.kyvislabs.api.client.gateway;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.gateway.config.IdbMigrationStrategy;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.rpc.GatewayRpcImplementation;
import com.inductiveautomation.ignition.common.gateway.services.ProtoRpcSerializer;
import com.inductiveautomation.ignition.gateway.web.resources.MountPathAlias;
import com.inductiveautomation.ignition.gateway.web.resources.SystemJsModule;
import com.kyvislabs.api.client.common.scripting.ClientAPIsScriptModule;
import com.kyvislabs.api.client.common.scripting.ScriptFunctionsScriptModulePyWrapper;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import com.kyvislabs.api.client.gateway.records.APIMigrationStrategy;
import com.kyvislabs.api.client.gateway.records.ResourceTypes;
import com.kyvislabs.api.client.gateway.scripting.ScriptFunctionsScriptModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class GatewayHook extends AbstractGatewayModuleHook {

    private final Logger logger = LoggerFactory.getLogger("API.Gateway.Hook");

    public static final SystemJsModule JS_MODULE =
            new SystemJsModule("com.kyvislabs.api.client", "/res/api-client/api-client.js");

    private ScriptFunctionsScriptModule scriptModule;
    private APIManager apiManager;
    private GatewayContext gatewayContext;

    @Override
    public void setup(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
        this.scriptModule = new ScriptFunctionsScriptModule(gatewayContext);
        this.apiManager = APIManager.get();
        this.apiManager.setGatewayContext(gatewayContext);

        // Register resource type with ConfigurationManager
        gatewayContext.getConfigurationManager()
                .getResourceTypeMetaRegistry()
                .register(ResourceTypes.API_RESOURCE_TYPE_META);

        // Register navigation page (React UI)
        gatewayContext.getWebResourceManager()
                .getNavigationModel()
                .getConnections()
                .addCategory("api-client", cat -> cat
                        .label("API Client")
                        .addPage("APIs", page -> page
                                .position(1)
                                .mount("/api-client/apis", "APIs", JS_MODULE)
                        )
                );
    }

    @Override
    public void startup(LicenseState licenseState) {
        try {
            apiManager.startup();
        } catch (Throwable ex) {
            logger.error("Error starting up API manager.", ex);
        }
    }

    @Override
    public void shutdown() {
        try {
            apiManager.shutdown();
        } catch (Throwable ex) {
            logger.error("Error shutting down API manager.", ex);
        }
    }

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);
        manager.addScriptModule(
                "system.api",
                new ScriptFunctionsScriptModulePyWrapper(scriptModule),
                new PropertiesFileDocProvider());
    }

    @Override
    public Optional<GatewayRpcImplementation> getRpcImplementation() {
        return Optional.of(GatewayRpcImplementation.newBuilder(ProtoRpcSerializer.DEFAULT_INSTANCE)
                .addInterface(scriptModule, ClientAPIsScriptModule.SERIALIZER)
                .build());
    }

    @Override
    public Optional<String> getMountedResourceFolder() {
        return Optional.of("mounted");
    }

    @Override
    public Optional<String> getMountPathAlias() {
        return Optional.of("api-client");
    }

    @Override
    public List<IdbMigrationStrategy> getRecordMigrationStrategies() {
        return List.of(new APIMigrationStrategy());
    }

    @Override
    public boolean isMakerEditionCompatible() {
        return true;
    }

    @Override
    public boolean isFreeModule() {
        return true;
    }
}
