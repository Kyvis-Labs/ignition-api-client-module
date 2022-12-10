package com.kyvislabs.api.client.gateway.api.authentication;

import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            long id = Long.valueOf(state.replace("?id=", ""));
            API api = APIManager.get().getAPI(id);
            api.getVariables().setVariable(OAuth2.VARIABLE_AUTHORIZATION_CODE, code);
            api.getVariables().clearVariable(OAuth2.VARIABLE_ACCESS_TOKEN);
            api.getVariables().clearVariable(OAuth2.VARIABLE_REFRESH_TOKEN);
            api.getVariables().clearVariable(OAuth2.VARIABLE_EXPIRATION);
            api.getGatewayContext().getPersistenceInterface().notifyRecordUpdated(api.getRecord());
            apiName = api.getName();
            success = true;
        } catch (Throwable ex) {
            logger.error("OAuth2: Error processing get response", ex);
        }

        resp.setContentType("text/html");
        PrintWriter out = resp.getWriter();

        out.println("<html>");
        out.println("<head>");
        out.println("<title>OAuth2 " + (success ? "Success" : "Failed") + "</title>");
        out.println("</head>");
        out.println("<body>");

        if (success) {
            out.println("<p>Congratulations! API " + apiName + " has authenticated successfully.</p>");
            out.println("<p>Please navigate back to the configuration page.</p>");
        } else {
            out.println("<p>Authentication failed, please try again.</p>");
        }

        out.println("</body>");
        out.println("</html>");
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
