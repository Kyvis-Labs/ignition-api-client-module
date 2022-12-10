package com.kyvislabs.api.client.gateway.api.webhooks;

import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class WebhookServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger("API.Webhook.Servlet");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(501, "Not Implemented");
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(501, "Not Implemented");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String response = IOUtils.toString(req.getReader());
            logger.debug("Webhook URI: " + req.getRequestURI());
            logger.debug("Webhook response: " + response);

            String[] uriParts = req.getRequestURI().split("/");

            if (uriParts.length == 6) {
                long apiId = Long.valueOf(uriParts[uriParts.length - 3]);
                String webhookName = uriParts[uriParts.length - 2];
                String webhookId = uriParts[uriParts.length - 1];
                API api = APIManager.get().getAPI(apiId);
                Webhook webhook = api.getWebhooks().getWebhook(webhookName);
                webhook.getWebhookKey(webhookId).handleResponse(200, req.getContentType(), response);
            }
        } catch (Throwable ex) {
            logger.error("Webhook: Error processing post response", ex);
        }
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
