package com.kyvislabs.api.client.gateway.api.functions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.Headers;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.managers.TagBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Function implements VariableStore {
    private Logger logger;
    private API api;
    private String name;
    private String tagPrefix;
    private ValueString url;
    private Method method;
    private Headers headers;
    private List<Parameter> parameters;
    private Body body;
    private ResponseType responseType;
    private ResponseFormat responseFormat;
    private Schedule schedule;
    private Actions actions;
    private String depends;
    private boolean dependsAlways;
    private boolean redirectNoHeaders;
    private boolean hasExecuted;
    private List<Integer> allowedErrorCodes;
    private Map<String, Object> localVariables;
    private FunctionStatus status;

    public Function(API api, String name) {
        this(api, name, null);
    }

    public Function(API api, String name, String tagPrefix) {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Function.%s", api.getName(), (tagPrefix == null ? "" : (tagPrefix.replace("/", ".") + ".")) + name));
        this.api = api;
        this.name = name;
        this.tagPrefix = tagPrefix;
        this.responseFormat = new ResponseFormat(this);
        this.actions = new Actions(this);
        this.body = new Body(this);
        this.headers = new Headers(api);
        this.localVariables = new ConcurrentHashMap<>();
        this.hasExecuted = false;
        this.allowedErrorCodes = Collections.synchronizedList(new ArrayList<>());
    }

    public void parse(Map yamlMap) throws APIException {
        parse(yamlMap, false);
    }

    public void parse(Map yamlMap, boolean skipUrlCheck) throws APIException {
        try {
            if (!skipUrlCheck) {
                if (!yamlMap.containsKey("url")) {
                    throw new APIException("Missing URL");
                }
                url = ValueString.parseValueString(api, yamlMap, "url", true);
            } else {
                url = null;
            }

            method = Method.valueOf(yamlMap.getOrDefault("method", "get").toString().toUpperCase());
            headers.parse(yamlMap);
            parameters = Parameter.parseParameters(this.getApi(), yamlMap);
            body.parse(yamlMap);
            responseType = ResponseType.valueOf(yamlMap.getOrDefault("responseType", "none").toString().toUpperCase());
            responseFormat.parse(yamlMap);
            schedule = Schedule.parseSchedule(this, yamlMap);
            actions.parse(yamlMap);

            depends = (String) yamlMap.getOrDefault("depends", null);
            dependsAlways = false;
            if (depends == null) {
                depends = (String) yamlMap.getOrDefault("dependsAlways", null);
                if (depends != null) {
                    dependsAlways = true;
                }
            }

            redirectNoHeaders = (boolean) yamlMap.getOrDefault("redirectNoHeaders", false);

            if (yamlMap.containsKey("allowedErrorCodes")) {
                List codesList = (List) yamlMap.get("allowedErrorCodes");
                for (Object codesObj : codesList) {
                    Map codesMap = (Map) codesObj;
                    allowedErrorCodes.add((Integer) codesMap.get("code"));
                }
            }

            if (!skipUrlCheck) {
                registerUDTs();
            }
        } catch (Throwable ex) {
            throw new APIException("Error parsing function '" + name + "': " + ex.getMessage(), ex);
        }
    }

    public void startup() throws APIException {
        try {
            logger.debug("Starting up");

            initTags();

            if (getSchedule() != null) {
                getSchedule().schedule(logger, this);
            }
        } catch (Throwable ex) {
            throw new APIException("Error starting up function '" + name + "': " + ex.getMessage(), ex);
        }
    }

    public void shutdown() {
        logger.debug("Shutting down");
        if (getSchedule() != null) {
            getSchedule().shutdown();
        }
        getActions().shutdown();
    }

    public synchronized API getApi() {
        return api;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized String getPrefix() {
        return tagPrefix;
    }

    public synchronized String getLoggerName() {
        return (getPrefix() == null ? "" : (getPrefix().replace("/", ".") + ".")) + getName();
    }

    public synchronized ValueString getUrl() {
        return url;
    }

    public synchronized Method getMethod() {
        return method;
    }

    public synchronized Headers getHeaders() {
        return headers;
    }

    public synchronized List<Parameter> getParameters() {
        return parameters;
    }

    public synchronized Body getBody() {
        return body;
    }

    public synchronized ResponseType getResponseType() {
        return responseType;
    }

    public synchronized ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public synchronized Schedule getSchedule() {
        return schedule;
    }

    public synchronized Actions getActions() {
        return actions;
    }

    public synchronized String getDepends() {
        return depends;
    }

    public synchronized boolean isDependsAlways() {
        return dependsAlways;
    }

    public synchronized boolean isRedirectNoHeaders() {
        return redirectNoHeaders;
    }

    public synchronized List<Integer> getAllowedErrorCodes() {
        return allowedErrorCodes;
    }

    public synchronized boolean isHasExecuted() {
        return hasExecuted;
    }

    public synchronized void setHasExecuted() {
        this.hasExecuted = true;
    }

    public synchronized Map<String, Object> getLocalVariables() {
        return localVariables;
    }

    private String getTagPrefix() {
        String prefix = getPrefix();
        if (prefix == null) {
            prefix = "Functions";
        }
        return String.format("%s/%s/%s", api.getName(), prefix, getName());
    }

    public Date getNextExecution() {
        if (getSchedule() != null) {
            return getSchedule().getNextDate();
        }

        return null;
    }

    private void registerUDTs() throws APIException {
        try {
            TagBuilder builder = TagBuilder.createUDTInstance("Functions/Status", String.format("%s/Status", getTagPrefix()));
            api.getTagManager().registerUDT(builder.build());
        } catch (Throwable ex) {
            throw new APIException("Error registering UDT instance", ex);
        }
    }

    private void initTags() {
        updateStatusTag("State", State.PENDING.getDisplay());
        setStatus(FunctionStatus.UNKNOWN);

        if (getSchedule() != null) {
            updateStatusTag("Schedule", getSchedule().toString());
        }
    }

    public synchronized void setStatus(FunctionStatus status) {
        this.status = status;
        updateStatusTag("Status", status.getDisplay());
    }

    public synchronized FunctionStatus getStatus() {
        return status == null ? FunctionStatus.UNKNOWN : status;
    }

    public void updateStatusTag(String tag, Object value) {
        api.getTagManager().tagUpdate(String.format("%s/Status/%s", getTagPrefix(), tag), value);
    }

    @Override
    public synchronized String getStoreName() {
        return name;
    }

    @Override
    public void setVariable(String name, Object value) {
        getLocalVariables().put(name, value);
    }

    @Override
    public synchronized Object getVariable(String name) throws APIException {
        if (getLocalVariables().containsKey(name)) {
            return getLocalVariables().get(name);
        }

        throw new APIException("Variable '" + name + "' doesn't exist");
    }

    public Integer callBlocking(VariableStore store) {
        return (new FunctionExecutor(logger, this, store)).call();
    }

    public void executeBlocking(VariableStore store) {
        (new FunctionExecutor(logger, this, store)).run();
    }

    public void executeAsync(VariableStore store) {
        api.getGatewayContext().getExecutionManager().executeOnce(new FunctionExecutor(logger, this, store));
    }

    public enum Method {
        GET,
        POST,
        PUT,
        DELETE,
        HEAD,
        PATCH
    }

    public enum ResponseType {
        NONE("none"),
        JSON("application/json"),
        XML("text/xml"),
        BYTES("application/octet-stream");

        private String contentType;

        ResponseType(String contentType) {
            this.contentType = contentType;
        }

        public String getContentType() {
            return contentType;
        }
    }

    public enum State {
        PENDING("Pending"),
        RUNNING("Running");

        private String display;

        State(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    public enum FunctionStatus {
        UNKNOWN("Unknown"),
        SUCCESS("Success"),
        FAILED("Failed"),
        DISABLED("Disabled"),
        TRIAL_EXPIRED("Trial Expired");

        private String display;

        FunctionStatus(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }
}
