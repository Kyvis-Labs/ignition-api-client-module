package com.kyvislabs.api.client.gateway.api.functions.actions.actions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.common.scripting.AbstractScriptFunctionsScriptModule;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.database.APIFileRecord;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class StoreFileServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger("API.StoreFile.Servlet");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String response = IOUtils.toString(req.getReader());
            logger.debug("Store file URI: " + req.getRequestURI());
            logger.debug("Store file response: " + response);

            String[] uriParts = req.getRequestURI().split("/");
            long apiId = Long.valueOf(uriParts[uriParts.length - 2]);
            String accessToken = uriParts[uriParts.length - 1];
            API api = APIManager.get().getAPI(apiId);

            SQuery<APIFileRecord> query = new SQuery<>(APIFileRecord.META);
            query.eq(APIFileRecord.APIId, apiId);
            query.eq(APIFileRecord.AccessToken, accessToken);
            APIFileRecord record = api.getGatewayContext().getPersistenceInterface().queryOne(query);
            if (record != null) {
                String fileName = record.getFileName();
                File dataDir = api.getGatewayContext().getSystemManager().getDataDir();
                File moduleDir = new File(dataDir, "modules/" + AbstractScriptFunctionsScriptModule.MODULE_ID);
                File apiDir = new File(moduleDir, api.getName());
                File file = new File(apiDir, fileName + "." + record.getExtension());
                resp.setContentType(record.getContentType());
                FileUtils.copyFile(file, resp.getOutputStream());
            } else {
                throw new APIException("Access token '" + accessToken + "' invalid");
            }
        } catch (Throwable ex) {
            logger.error("Store file: Error processing post response", ex);
        }
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(501, "Not Implemented");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(501, "Not Implemented");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(501, "Not Implemented");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(501, "Not Implemented");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(501, "Not Implemented");
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(501, "Not Implemented");
    }
}
