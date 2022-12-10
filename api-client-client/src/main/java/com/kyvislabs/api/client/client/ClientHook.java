package com.kyvislabs.api.client.client;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.vision.api.client.AbstractClientModuleHook;
import com.kyvislabs.api.client.client.scripting.ScriptFunctionsScriptModule;

public class ClientHook extends AbstractClientModuleHook {

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);

        manager.addScriptModule(
                "system.api",
                new ScriptFunctionsScriptModule(),
                new PropertiesFileDocProvider()
        );
    }

}
