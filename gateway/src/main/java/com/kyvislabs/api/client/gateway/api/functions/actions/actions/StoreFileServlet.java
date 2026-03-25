package com.kyvislabs.api.client.gateway.api.functions.actions.actions;

import com.kyvislabs.api.client.common.scripting.AbstractScriptFunctionsScriptModule;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import com.kyvislabs.api.client.gateway.api.API;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class StoreFileServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger("API.StoreFile.Servlet");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            logger.debug("Store file URI: " + req.getRequestURI());

            String[] uriParts = req.getRequestURI().split("/");
            String apiName = uriParts[uriParts.length - 2];
            String accessToken = uriParts[uriParts.length - 1];

            API api = APIManager.get().getAPI(apiName);
            File dataDir = api.getGatewayContext().getSystemManager().getDataDir();
            File moduleDir = new File(dataDir, "modules/" + AbstractScriptFunctionsScriptModule.MODULE_ID);
            File apiDir = new File(moduleDir, api.getName());

            // Find file by access token (scan directory for matching token file)
            File tokenFile = new File(apiDir, accessToken + ".token");
            if (tokenFile.exists()) {
                String fileName = Files.readString(tokenFile.toPath()).trim();
                File file = new File(apiDir, fileName);
                if (file.exists()) {
                    String contentType = Files.probeContentType(file.toPath());
                    if (contentType != null) resp.setContentType(contentType);
                    FileUtils.copyFile(file, resp.getOutputStream());
                } else {
                    resp.sendError(404, "File not found");
                }
            } else {
                resp.sendError(403, "Invalid access token");
            }
        } catch (Throwable ex) {
            logger.error("Store file: Error processing get response", ex);
            resp.sendError(500, "Internal server error");
        }
    }

    @Override protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
}
