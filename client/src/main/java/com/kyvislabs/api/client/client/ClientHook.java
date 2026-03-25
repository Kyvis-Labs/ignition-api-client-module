package com.kyvislabs.api.client.client;

import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnection;
import com.inductiveautomation.ignition.common.gateway.services.ProtoRpcSerializer;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.vision.api.client.AbstractClientModuleHook;
import com.kyvislabs.api.client.common.scripting.ClientAPIsScriptModule;
import com.kyvislabs.api.client.common.scripting.ScriptFunctionsScriptModulePyWrapper;
import com.kyvislabs.api.client.common.scripting.interfaces.APIsInterface;

public class ClientHook extends AbstractClientModuleHook {

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);

        APIsInterface rpc = GatewayConnection.getRpcInterface(
                ProtoRpcSerializer.DEFAULT_INSTANCE,
                "com.kyvislabs.api.client",
                APIsInterface.class
        );

        manager.addScriptModule(
                "system.api",
                new ScriptFunctionsScriptModulePyWrapper(new ClientAPIsScriptModule(rpc)),
                new PropertiesFileDocProvider()
        );
    }
}
