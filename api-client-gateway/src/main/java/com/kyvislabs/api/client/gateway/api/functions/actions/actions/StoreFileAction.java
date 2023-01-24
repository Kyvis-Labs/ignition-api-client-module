package com.kyvislabs.api.client.gateway.api.functions.actions.actions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.AbstractScriptFunctionsScriptModule;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.Action;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.database.APIFileRecord;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

import java.io.File;
import java.util.Date;
import java.util.Map;

public class StoreFileAction extends Action {
    public static final String ACTION = "storefile";
    public static final String SERVLET_PATH = "api-file";

    private Logger logger;
    private ValueString fileId;
    private ValueString fileName;
    private String extension;
    private String contentType;
    private ValueString path;

    public StoreFileAction(Function function) {
        super(function);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s.Action.StoreFile", function.getApi().getName(), function.getLoggerName()));
    }

    @Override
    public void parse(Map yamlMap) throws APIException {
        super.parse(yamlMap);

        if (!yamlMap.containsKey("fileId")) {
            throw new APIException("File id missing");
        }

        if (!yamlMap.containsKey("fileName")) {
            throw new APIException("File name missing");
        }

        this.fileId = ValueString.parseValueString(function.getApi(), yamlMap, "fileId", true);
        this.fileName = ValueString.parseValueString(function.getApi(), yamlMap, "fileName", true);
        this.extension = (String) yamlMap.getOrDefault("extension", "jpeg");
        this.contentType = (String) yamlMap.getOrDefault("contentType", "image/jpeg");
        this.path = ValueString.parseValueString(function.getApi(), yamlMap, "path", function.getName());
    }

    public synchronized ValueString getFileId() {
        return fileId;
    }

    public synchronized ValueString getFileName() {
        return fileName;
    }

    public synchronized String getExtension() {
        return extension;
    }

    public synchronized String getContentType() {
        return contentType;
    }

    public synchronized ValueString getPath() {
        return path;
    }

    private String getServletPath(String accessToken) {
        return "/system/" + SERVLET_PATH + "/" + function.getApi().getId() + "/" + accessToken;
    }

    private String generateAccessToken() {
        return RandomStringUtils.randomAlphanumeric(20);
    }

    @Override
    public void handleResponse(VariableStore store, int statusCode, String contentType, String response) throws APIException {
        try {
            byte[] fileBytes = Base64.decodeBase64(response);
            String api = function.getApi().getName();
            String fileId = getFileId().getValue(store, response);
            String fileName = getFileName().getValue(store, response);
            String tagPath = getPath().getValue(store, response);
            String accessToken = generateAccessToken();

            File dataDir = function.getApi().getGatewayContext().getSystemManager().getDataDir();
            File moduleDir = new File(dataDir, "modules/" + AbstractScriptFunctionsScriptModule.MODULE_ID);
            if (!moduleDir.exists()) {
                moduleDir.mkdir();
            }
            File apiDir = new File(moduleDir, api);
            if (!apiDir.exists()) {
                apiDir.mkdir();
            }

            FileUtils.writeByteArrayToFile(new File(apiDir, fileName + "." + getExtension()), fileBytes);
            function.getApi().getTagManager().tagUpdate(tagPath + "/FileURL", getServletPath(accessToken));

            SQuery<APIFileRecord> query = new SQuery<>(APIFileRecord.META);
            query.eq(APIFileRecord.APIId, function.getApi().getId());
            query.eq(APIFileRecord.FileName, fileName);

            APIFileRecord record = function.getApi().getGatewayContext().getPersistenceInterface().queryOne(query);
            if (record == null) {
                record = function.getApi().getGatewayContext().getPersistenceInterface().createNew(APIFileRecord.META);
                record.setAPIId(function.getApi().getId());
                record.setFileName(fileName);
            }

            record.setFileId(fileId);
            record.setExtension(getExtension());
            record.setContentType(getContentType());
            record.setAccessToken(accessToken);
            record.setLastUpdate(new Date());
            function.getApi().getGatewayContext().getPersistenceInterface().save(record);
        } catch (Throwable ex) {
            throw new APIException("Error handling store file action", ex);
        }
    }
}
