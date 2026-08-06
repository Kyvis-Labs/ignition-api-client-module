package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Collectors;

public class OAuth2Servlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger("API.OAuth2.Servlet");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        boolean success = false;
        String apiName = "Unknown";

        try {
            logger.debug("OAuth2 URI: " + req.getRequestURI());
            logger.debug("OAuth2 parameters: " + req.getParameterMap().keySet().stream()
                    .map(key -> key + "=" + String.join(",", req.getParameterMap().get(key)))
                    .collect(Collectors.joining(", ", "{", "}")));

            String code = req.getParameter("code");
            String state = req.getParameter("state");
            // State now contains the API name (8.3 uses names instead of numeric IDs)
            apiName = state != null ? state.replace("?name=", "") : state;
            API api = APIManager.get().getAPI(apiName);
            // Batched into one persist/reload instead of four - each individual setVariable/
            // clearVariable call persists and triggers a full async API reload, and a reload
            // triggered mid-sequence here could swap in a freshly-reloaded instance whose own
            // functions start authenticating concurrently with this method's remaining calls,
            // racing to persist two different views of the same variables (the new instance seeing
            // the auth code but not yet the cleared stale token, or vice versa).
            api.getVariables().batchUpdate(() -> {
                api.getVariables().setVariable(OAuth2.VARIABLE_AUTHORIZATION_CODE, code);
                api.getVariables().clearVariable(OAuth2.VARIABLE_ACCESS_TOKEN);
                api.getVariables().clearVariable(OAuth2.VARIABLE_REFRESH_TOKEN);
                api.getVariables().clearVariable(OAuth2.VARIABLE_EXPIRATION);
            });
            success = true;
        } catch (Throwable ex) {
            logger.error("OAuth2: Error processing get response", ex);
        }

        resp.setContentType("text/html");
        PrintWriter out = resp.getWriter();
        out.println("<html><head><title>OAuth2 " + (success ? "Success" : "Failed") + "</title></head><body>");
        if (success) {
            out.println("<p>Congratulations! API " + apiName + " has authenticated successfully.</p>");
            out.println("<p>Please navigate back to the configuration page.</p>");
        } else {
            out.println("<p>Authentication failed, please try again.</p>");
        }
        out.println("</body></html>");
    }

    @Override protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
    @Override protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { resp.sendError(501, "Not Implemented"); }
}
