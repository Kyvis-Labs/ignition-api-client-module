package com.kyvislabs.api.client.gateway;

import com.google.common.collect.Lists;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.gateway.clientcomm.ClientReqSession;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.web.models.ConfigCategory;
import com.inductiveautomation.ignition.gateway.web.models.IConfigTab;
import com.kyvislabs.api.client.gateway.database.*;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import com.kyvislabs.api.client.gateway.pages.APIManagerPage;
import com.kyvislabs.api.client.gateway.scripting.ScriptFunctionsScriptModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class GatewayHook extends AbstractGatewayModuleHook {

    private final Logger logger = LoggerFactory.getLogger("API.Gateway.Hook");

    public static final ConfigCategory CONFIG_CATEGORY = new ConfigCategory("API", "API.MenuTitle");

    private ScriptFunctionsScriptModule scriptModule, scriptModuleRPC;
    private APIManager apiManager;
    private GatewayContext gatewayContext;

    @Override
    public void setup(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
        this.scriptModule = new ScriptFunctionsScriptModule(gatewayContext, false);
        this.scriptModuleRPC = new ScriptFunctionsScriptModule(gatewayContext, true);
        this.apiManager = APIManager.get();
        this.apiManager.setGatewayContext(gatewayContext);

        try {
            gatewayContext.getSchemaUpdater().updatePersistentRecords(APIRecord.META);
            gatewayContext.getSchemaUpdater().updatePersistentRecords(APIVariableRecord.META);
            gatewayContext.getSchemaUpdater().updatePersistentRecords(APIWebhookRecord.META);
            gatewayContext.getSchemaUpdater().updatePersistentRecords(APIFileRecord.META);
            gatewayContext.getSchemaUpdater().updatePersistentRecords(APICertificateRecord.META);
        } catch (SQLException ex) {
            logger.error("Error verifying schemas.", ex);
        }

        BundleUtil.get().addBundle("API", APIRecord.class, "API");
    }

    @Override
    public void notifyLicenseStateChanged(LicenseState licenseState) {
        try {
            apiManager.setLicenseState(licenseState);
            apiManager.shutdown();
            apiManager.startup();
        } catch (Throwable ex) {
            logger.error("Error restarting API manager.", ex);
        }
    }

    @Override
    public void startup(LicenseState licenseState) {
        try {
            apiManager.setLicenseState(licenseState);
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

        BundleUtil.get().removeBundle("API");
    }

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);

        manager.addScriptModule(
                "system.api",
                scriptModule,
                new PropertiesFileDocProvider());
    }

    @Override
    public Object getRPCHandler(ClientReqSession session, String projectName) {
        return scriptModuleRPC;
    }

    @Override
    public List<? extends IConfigTab> getConfigPanels() {
        return Lists.newArrayList(APIManagerPage.MENU_ENTRY);
    }

    @Override
    public List<ConfigCategory> getConfigCategories() {
        return Lists.newArrayList(CONFIG_CATEGORY);
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
