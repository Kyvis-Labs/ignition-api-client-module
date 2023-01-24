package com.kyvislabs.api.client.gateway.api.functions.actions.runif;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.functions.actions.RunIf;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.database.APIFileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

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
        String fileId = getFileId().getValue(store, response);
        String fileName = getFileName().getValue(store, response);
        SQuery<APIFileRecord> query = new SQuery<>(APIFileRecord.META);
        query.eq(APIFileRecord.APIId, function.getApi().getId());
        query.eq(APIFileRecord.FileName, fileName);
        query.eq(APIFileRecord.FileName, fileId);
        APIFileRecord record = function.getApi().getGatewayContext().getPersistenceInterface().queryOne(query);
        boolean ret = record == null;
        logger.debug("Checking for file [fileId=" + fileId + ", fileName=" + fileName + ", proceed=" + ret + "]");
        return ret;
    }
}
