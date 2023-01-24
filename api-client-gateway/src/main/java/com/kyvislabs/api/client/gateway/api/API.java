package com.kyvislabs.api.client.gateway.api;

import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.managed.WriteHandler;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.database.APICertificateRecord;
import com.kyvislabs.api.client.gateway.database.APIRecord;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import com.kyvislabs.api.client.gateway.managers.CertificateManager;
import com.kyvislabs.api.client.gateway.managers.TagManager;
import net.dongliu.requests.RequestBuilder;
import net.dongliu.requests.Requests;
import net.dongliu.requests.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import simpleorm.dataset.SQuery;

import java.security.KeyStore;
import java.util.Map;
import java.util.Optional;

public class API implements WriteHandler {
    private static final Yaml yaml = new Yaml();

    private Logger logger;
    private APIManager apiManager;
    private APIRecord record;
    private Variables variables;
    private AuthType authType;
    private Headers headers;
    private Session session;
    private boolean httpsVerification, httpsCertificates;
    private KeyStore keyStore;
    private String certificate, privateKey;
    private Webhooks webhooks;
    private Functions functions;
    private APIStatus status;

    public API(APIManager apiManager, APIRecord record) {
        this.apiManager = apiManager;
        this.record = record;
        this.logger = LoggerFactory.getLogger("API." + record.getName());
        this.variables = new Variables(this);
        this.authType = new AuthType(this);
        this.headers = new Headers(this);
        this.webhooks = new Webhooks(this);
        this.functions = new Functions(this);
        loadConfiguration();
    }

    private void loadConfiguration() {
        String config = record.getConfiguration();
        logger.debug("Loading configuration: " + config);
        Map yamlMap = yaml.load(config);
        if (yamlMap == null) {
            setStatus(APIStatus.EMPTY_CONFIGURATION);
        } else {
            setStatus(APIStatus.INITIALIZING);

            try {
                Integer version = Integer.valueOf((String) yamlMap.getOrDefault("version", 1));

                authType.parse(version, yamlMap);

                if (authType.requiresSession() || Boolean.valueOf(yamlMap.getOrDefault("session", "false").toString())) {
                    session = Requests.session();
                }

                httpsVerification = Boolean.valueOf(yamlMap.getOrDefault("httpsVerification", "true").toString());
                httpsCertificates = Boolean.valueOf(yamlMap.getOrDefault("httpsCertificates", "false").toString());

                if (httpsCertificates) {
                    SQuery<APICertificateRecord> query = new SQuery<>(APICertificateRecord.META);
                    query.eq(APICertificateRecord.APIId, getId());
                    APICertificateRecord certificateRecord = getGatewayContext().getPersistenceInterface().queryOne(query);
                    if (certificateRecord == null) {
                        setStatus(APIStatus.MISSING_CERTIFICATE);
                    } else {
                        if (certificateRecord.getCertificate() == null || certificateRecord.getCertificate().equals("") || certificateRecord.getPrivateKey() == null || certificateRecord.getPrivateKey().equals("")) {
                            setStatus(APIStatus.MISSING_CERTIFICATE);
                        } else {
                            certificate = certificateRecord.getCertificate();
                            privateKey = certificateRecord.getPrivateKey();

                            try {
                                this.keyStore = CertificateManager.loadKeyStore(certificate, privateKey, Optional.empty());
                            } catch (Throwable t) {
                                this.keyStore = null;
                                logger.error("Error creating certificate keystore", t);
                            }
                        }
                    }
                }

                headers.parse(version, yamlMap);
                variables.parse(version, yamlMap);
                webhooks.parse(version, yamlMap);
                functions.parse(version, yamlMap);

                authType.initializeVariables();
                if (!variables.initComplete()) {
                    setStatus(APIStatus.MISSING_VARIABLES);
                } else if (!getStatus().equals(APIStatus.MISSING_CERTIFICATE)) {
                    setStatus(APIStatus.INITIALIZED);
                }
            } catch (Throwable ex) {
                setStatus(APIStatus.FAULTED);
                logger.error("Error loading configuration: " + ex.getMessage(), ex);
            }
        }
    }

    public void startup() {
        logger.debug("Starting up");

        try {
            if (!apiManager.isLicensed() && apiManager.isTrialExpired()) {
                setStatus(APIStatus.TRIAL_EXPIRED);
                functions.expired();
                webhooks.expired();
            } else if (!record.isEnabled()) {
                setStatus(APIStatus.DISABLED);
                functions.disable();
                webhooks.disable();
            } else if (record.isEnabled() && getStatus().equals(APIStatus.INITIALIZED)) {
                if (!getAuthType().isAuthorized()) {
                    setStatus(APIStatus.NEEDS_AUTHORIZATION);
                } else {
                    setStatus(APIStatus.STARTING);
                    webhooks.startup();
                    functions.startup();
                    setStatus(APIStatus.RUNNING);
                }
            }
        } catch (Throwable ex) {
            setStatus(APIStatus.FAULTED);
            logger.error("Error starting up: " + ex.getMessage(), ex);
        }
    }

    public void shutdown() {
        logger.debug("Shutting down");
        webhooks.shutdown();
        functions.shutdown();
    }

    public synchronized long getId() {
        return record.getId();
    }

    public synchronized String getName() {
        return record.getName();
    }

    public synchronized String getStatusDisplay() {
        return status.getDisplay();
    }

    public synchronized APIStatus getStatus() {
        return status == null ? APIStatus.INITIALIZING : status;
    }

    public synchronized void setStatus(APIStatus status) {
        this.status = status;
        updateStatusTag("Status", status.getDisplay());
    }

    public synchronized GatewayContext getGatewayContext() {
        return apiManager.getGatewayContext();
    }

    public synchronized TagManager getTagManager() {
        return apiManager.getTagManager();
    }

    public synchronized Variables getVariables() {
        return variables;
    }

    public synchronized Headers getHeaders() {
        return headers;
    }

    public synchronized AuthType getAuthType() {
        return authType;
    }

    public synchronized Session getSession() {
        return session;
    }

    public synchronized boolean isHttpsVerification() {
        return httpsVerification;
    }

    public synchronized boolean isHttpsCertificates() {
        return httpsCertificates;
    }

    public synchronized KeyStore getKeyStore() {
        return keyStore;
    }

    public synchronized Webhooks getWebhooks() {
        return webhooks;
    }

    public synchronized Functions getFunctions() {
        return functions;
    }

    public synchronized APIRecord getRecord() {
        return record;
    }

    private String getTagPrefix() {
        return String.format("%s", getName());
    }

    public void updateStatusTag(String tag, Object value) {
        getTagManager().tagUpdate(String.format("%s/%s", getTagPrefix(), tag), value);
    }

    public RequestBuilder getRequestBuilder(String url, Function.Method method) {
        RequestBuilder builder = null;
        Session session = getSession();
        if (session == null) {
            switch (method) {
                case POST:
                    builder = Requests.post(url);
                    break;
                case PUT:
                    builder = Requests.put(url);
                    break;
                case DELETE:
                    builder = Requests.delete(url);
                    break;
                case HEAD:
                    builder = Requests.head(url);
                    break;
                case PATCH:
                    builder = Requests.patch(url);
                    break;
                default:
                    builder = Requests.get(url);
                    break;
            }
        } else {
            switch (method) {
                case POST:
                    builder = session.post(url);
                    break;
                case PUT:
                    builder = session.put(url);
                    break;
                case DELETE:
                    builder = session.delete(url);
                    break;
                case HEAD:
                    builder = session.head(url);
                    break;
                case PATCH:
                    builder = session.patch(url);
                    break;
                default:
                    builder = session.get(url);
                    break;
            }
        }

        if (!isHttpsVerification()) {
            builder.verify(false);
        }

        if (isHttpsCertificates() && getKeyStore() != null) {
            builder.keyStore(getKeyStore());
        } else {
            if (apiManager.getKeyStore() != null) {
                builder.keyStore(apiManager.getKeyStore());
            }
        }

        return builder;
    }

    @Override
    public QualityCode write(TagPath tagPath, Object o) {
        try {
            getTagManager().tagUpdate(tagPath.toStringFull(), o);
            return QualityCode.Good;
        } catch (Throwable ex) {
            logger.error("Error in write handler for '" + tagPath.toStringFull() + "'", ex);
            return QualityCode.Error;
        }
    }

    public enum APIStatus {
        DISABLED("Disabled"),
        EMPTY_CONFIGURATION("Empty Configuration"),
        MISSING_CERTIFICATE("Missing Certificate"),
        MISSING_VARIABLES("Missing Variables"),
        INITIALIZING("Initializing"),
        INITIALIZED("Initialized"),
        FAULTED("Faulted"),
        STARTING("Starting"),
        TRIAL_EXPIRED("Trial Expired"),
        NEEDS_AUTHORIZATION("Needs Authorization"),
        NEEDS_2FA_CODE("Needs 2FA Code"),
        RUNNING("Running");

        private String display;

        APIStatus(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }
}
