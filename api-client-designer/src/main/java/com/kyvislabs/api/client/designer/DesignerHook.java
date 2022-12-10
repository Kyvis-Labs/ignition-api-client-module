package com.kyvislabs.api.client.designer;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.kyvislabs.api.client.client.scripting.ScriptFunctionsScriptModule;

public class DesignerHook extends AbstractDesignerModuleHook {

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
