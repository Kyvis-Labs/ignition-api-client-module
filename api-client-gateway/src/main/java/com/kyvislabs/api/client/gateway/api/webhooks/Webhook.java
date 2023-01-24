package com.kyvislabs.api.client.gateway.api.webhooks;

import com.inductiveautomation.ignition.common.gateway.HttpURL;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.database.APIWebhookRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Webhook {
    public static final String SERVLET_PATH = "webhook";

    private Logger logger;
    private API api;
    private String name;
    private ValueString defaultKey;
    private ValueString defaultId;
    private Integer defaultTTL;
    private boolean checkOnStart;
    private Function check;
    private Function add;
    private Function remove;
    private Function handle;
    private Map<String, WebhookKey> webhookKeys;

    public Webhook(API api, String name) {
        this.logger = LoggerFactory.getLogger(String.format("API.%s.Webhook.%s", api.getName(), name));
        this.api = api;
        this.name = name;
        this.webhookKeys = new ConcurrentHashMap<>();
    }

    public void parse(Integer version, Map yamlMap) throws APIException {
        try {
            if (!yamlMap.containsKey("check")) {
                throw new APIException("Missing webhook check function");
            }

            if (!yamlMap.containsKey("add")) {
                throw new APIException("Missing webhook add function");
            }

            if (!yamlMap.containsKey("remove")) {
                throw new APIException("Missing webhook remove function");
            }

            if (!yamlMap.containsKey("handle")) {
                throw new APIException("Missing webhook handle function");
            }

            checkOnStart = Boolean.valueOf(yamlMap.getOrDefault("checkOnStart", "false").toString());
            defaultKey = ValueString.parseValueString(api, yamlMap, "key");
            defaultId = ValueString.parseValueString(api, yamlMap, "id");

            Object ttl = yamlMap.getOrDefault("ttl", null);
            this.defaultTTL = ttl == null ? null : Integer.valueOf(ttl.toString());

            if (checkOnStart && defaultKey == null) {
                throw new APIException("Missing webhook key");
            }

            String tagPrefix = "Webhooks/" + getName();
            check = new Function(api, "check", tagPrefix);
            check.parse(version, (Map) yamlMap.get("check"));

            add = new Function(api, "add", tagPrefix);
            add.parse(version, (Map) yamlMap.get("add"));

            remove = new Function(api, "remove", tagPrefix);
            remove.parse(version, (Map) yamlMap.get("remove"));

            handle = new Function(api, "handle", tagPrefix);
            handle.parse(version, (Map) yamlMap.get("handle"), true);
        } catch (Throwable ex) {
            throw new APIException("Error parsing webhook '" + name + "': " + ex.getMessage(), ex);
        }
    }

    public void startup() throws APIException {
        try {
            logger.debug("Starting up");

            init();
        } catch (Throwable ex) {
            throw new APIException("Error starting up webhook '" + name + "': " + ex.getMessage(), ex);
        }
    }

    private void init() throws APIException {
        SQuery<APIWebhookRecord> query = new SQuery<>(APIWebhookRecord.META);
        query.eq(APIWebhookRecord.APIId, api.getId());
        query.eq(APIWebhookRecord.Name, name);
        for (APIWebhookRecord dbRecord : api.getGatewayContext().getPersistenceInterface().query(query)) {
            getWebhookKeys().put(dbRecord.getKey(), new WebhookKey(this, dbRecord, dbRecord.getKey(), dbRecord.getUId(), dbRecord.getUrl(), dbRecord.getTTL()));
        }

        if (isCheckOnStart()) {
            if (getWebhookKeys().size() == 0) {
                addWebhookKey(getDefaultKey().getValue(), getDefaultId() == null ? null : getDefaultId().getValue(), getDefaultTTL());
            }

            for (String webhookKey : getWebhookKeys().keySet()) {
                WebhookKey webhookKeyObj = getWebhookKeys().get(webhookKey);
                webhookKeyObj.execute();
            }
        }
    }

    private WebhookKey addWebhookKey(String key, String id, Integer ttl) {
        APIWebhookRecord dbRecord = api.getGatewayContext().getPersistenceInterface().createNew(APIWebhookRecord.META);
        dbRecord.setApiId(api.getId());
        dbRecord.setName(getName());
        dbRecord.setKey(key);
        dbRecord.setUId(id);
        dbRecord.setUrl(getServletUrl(key));
        dbRecord.setTTL(getWebhookTTLDate(ttl));
        api.getGatewayContext().getPersistenceInterface().save(dbRecord);
        WebhookKey webhookKeyObj = new WebhookKey(this, dbRecord, dbRecord.getKey(), dbRecord.getUId(), dbRecord.getUrl(), dbRecord.getTTL());
        getWebhookKeys().put(key, webhookKeyObj);
        return webhookKeyObj;
    }

    private String getServletPath(String key) {
        return Webhook.SERVLET_PATH + "/" + api.getId() + "/" + getName() + "/" + key;
    }

    public String getServletUrl(String key) {
        HttpURL httpUrl = api.getGatewayContext().getRedundancyManager().getAllHttpAddresses().getMasterAddresses().get(0);
        httpUrl.setPath("/system/" + getServletPath(key));
        return httpUrl.toStringHTTPS().replace(":443", "");
    }

    public void shutdown() {
        logger.debug("Shutting down");

        for (String webhookKey : getWebhookKeys().keySet()) {
            WebhookKey webhookKeyObj = getWebhookKeys().get(webhookKey);
            if (webhookKeyObj.getTtlFuture() != null) {
                webhookKeyObj.getTtlFuture().cancel(true);
            }
        }
    }

    public synchronized API getApi() {
        return api;
    }

    public Logger getLogger() {
        return logger;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized ValueString getDefaultKey() {
        return defaultKey;
    }

    public synchronized ValueString getDefaultId() {
        return defaultId;
    }

    public synchronized Integer getDefaultTTL() {
        return defaultTTL;
    }

    public Date getWebhookTTLDate(Integer webhookTTL) {
        if (webhookTTL != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_MONTH, webhookTTL);
            return cal.getTime();
        }

        return null;
    }

    public synchronized boolean isCheckOnStart() {
        return checkOnStart;
    }

    public synchronized Function getCheck() {
        return check;
    }

    public synchronized Function getAdd() {
        return add;
    }

    public synchronized Function getRemove() {
        return remove;
    }

    public synchronized Function getHandle() {
        return handle;
    }

    public synchronized Map<String, WebhookKey> getWebhookKeys() {
        return webhookKeys;
    }

    public WebhookKey getWebhookKey(String key) throws APIException {
        if (webhookKeys.containsKey(key)) {
            return webhookKeys.get(key);
        }

        throw new APIException("Webhook key '" + key + "' doesn't exist");
    }

    public WebhookKey getWebhookKeyOrCreate(String key, String id, Integer ttl) {
        if (webhookKeys.containsKey(key)) {
            return webhookKeys.get(key);
        } else {
            return addWebhookKey(key, id, ttl);
        }
    }

    public void expired() {
        getCheck().setStatus(Function.FunctionStatus.TRIAL_EXPIRED);
        getAdd().setStatus(Function.FunctionStatus.TRIAL_EXPIRED);
        getRemove().setStatus(Function.FunctionStatus.TRIAL_EXPIRED);
    }

    public void disable() {
        getCheck().setStatus(Function.FunctionStatus.DISABLED);
        getAdd().setStatus(Function.FunctionStatus.DISABLED);
        getRemove().setStatus(Function.FunctionStatus.DISABLED);
    }
}
