package com.kyvislabs.api.client.gateway.api.functions.actions.runif;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.AbstractScriptFunctionsScriptModule;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.RunIf;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

public class StoreFileIdNotExists extends RunIf {
    public static final String TYPE = "storeFileIdNotExists";

    private Logger logger;
    private ValueString fileId;
    private ValueString fileName;

    public StoreFileIdNotExists(Function function) {
        super(function);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Action.RunIf.StoreFileIdNotExists", function.getApi().getName(), function.getLoggerName()));
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        if (!yamlMap.containsKey("fileId")) {
            throw new APIException("File id missing");
        }

        if (!yamlMap.containsKey("fileName")) {
            throw new APIException("File name missing");
        }

        this.fileId = ValueString.parseValueString(function.getApi(), yamlMap, "fileId", true);
        this.fileName = ValueString.parseValueString(function.getApi(), yamlMap, "fileName", true);
    }

    public synchronized ValueString getFileId() {
        return fileId;
    }

    public synchronized ValueString getFileName() {
        return fileName;
    }

    @Override
    public boolean proceed(VariableStore store, String response) throws APIException {
        String resolvedFileId = getFileId().getValue(store, response);
        String resolvedFileName = getFileName().getValue(store, response);

        // In 8.3, check file existence on disk instead of querying the DB
        File dataDir = function.getApi().getGatewayContext().getSystemManager().getDataDir();
        File moduleDir = new File(dataDir, "modules/" + AbstractScriptFunctionsScriptModule.MODULE_ID);
        File apiDir = new File(moduleDir, function.getApi().getName());

        // Check if any .token file in apiDir maps to a file with resolvedFileName
        boolean fileExists = false;
        if (apiDir.exists() && apiDir.isDirectory()) {
            File[] extensions = apiDir.listFiles((dir, name) -> !name.endsWith(".token"));
            if (extensions != null) {
                for (File f : extensions) {
                    if (f.getName().startsWith(resolvedFileName)) {
                        fileExists = true;
                        break;
                    }
                }
            }
        }

        boolean ret = !fileExists;
        logger.debug("Checking for file [fileId=" + resolvedFileId + ", fileName=" + resolvedFileName + ", proceed=" + ret + "]");
        return ret;
    }
}
