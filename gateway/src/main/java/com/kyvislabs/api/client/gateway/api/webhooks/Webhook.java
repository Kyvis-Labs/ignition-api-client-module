package com.kyvislabs.api.client.gateway.api.webhooks;

import com.inductiveautomation.ignition.common.gateway.HttpURL;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

        // Webhook keys are runtime state (issued to/tracked against an external system), not config -
        // unlike variables/certificate, a change here shouldn't force the whole API to reload (it's
        // already running, mid-execution more often than not). Persisted straight to a file via
        // WebhookKeyStore (see there for why), instead of through api.persistResource() /
        // ConfigurationManager, which has no way to push a resource change without also triggering a
        // full async reload. Restoring a key here only makes it known/found again (e.g. by
        // getWebhookKeyOrCreate() below) - it does not, by itself, check/add/execute anything; see
        // init() for what runs automatically on startup and what waits for something else to call it.
        try {
            for (WebhookKeyStore.PersistedWebhookKey webhookKey : WebhookKeyStore.read(api.getGatewayContext().getSystemManager().getDataDir(), api.getName(), name)) {
                logger.debug("Restoring persisted webhook key '" + webhookKey.key() + "'");
                Date ttlDate = webhookKey.ttl() != null ? new Date(webhookKey.ttl()) : null;
                webhookKeys.put(webhookKey.key(), new WebhookKey(this, webhookKey.key(), webhookKey.id(), getServletUrl(webhookKey.key()), ttlDate));
            }
        } catch (Throwable t) {
            logger.error("Error reading persisted webhook keys", t);
        }
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
        // Only checkOnStart webhooks are driven from here - a checkOnStart webhook has no owning
        // function, so nothing else will ever check/add it or re-arm its periodic TTL recheck
        // (WebhookKey.schedule(), invoked at the end of a successful run). If the key isn't already
        // known (first run ever, or restored from a previous session found nothing - see the
        // constructor), create the default one from this config. Either way, execute() it: for a
        // brand-new key that's the initial check/add; for a restored one it's the re-verification and
        // TTL-recheck re-arm that survives a restart, since WebhookKey.exists and its recheck
        // ScheduledFuture are transient, in-memory-only state, not part of what's persisted.
        //
        // A WebhookAction-created key (checkOnStart == false) is restored into memory (in the
        // constructor) so getWebhookKeyOrCreate() can find and reuse it, but is deliberately NOT
        // executed here. Its owning function is what's responsible for checking/re-verifying it, on
        // that function's own schedule, whenever it next runs - the information persisted here is
        // available immediately, but nothing acts on it until that function calls in. Executing it
        // here too would just race that function's own invocation for no benefit, since this pass has
        // no way to supply whatever custom variables the function's WebhookAction would.
        if (isCheckOnStart()) {
            // Check for this specific key, not just "is the map non-empty" - defaultKey is a value
            // string, so its resolved value isn't necessarily constant across reloads (e.g. if it
            // references a variable that changed); comparing by size would skip creating the current
            // key if some other (now-stale) key happened to already be present.
            String key = getDefaultKey().getValue();
            if (!getWebhookKeys().containsKey(key)) {
                addWebhookKey(key, getDefaultId() == null ? null : getDefaultId().getValue(), getDefaultTTL());
            }

            for (WebhookKey webhookKeyObj : getWebhookKeys().values()) {
                webhookKeyObj.execute();
            }
        }
    }

    private WebhookKey addWebhookKey(String key, String id, Integer ttl) {
        String url = getServletUrl(key);
        Date ttlDate = getWebhookTTLDate(ttl);
        WebhookKey webhookKeyObj = new WebhookKey(this, key, id, url, ttlDate);
        getWebhookKeys().put(key, webhookKeyObj);
        persistWebhookKeys();
        return webhookKeyObj;
    }

    /**
     * Persists this webhook's current keys to disk, so externally-issued webhook keys survive a
     * gateway restart, without touching the ConfigurationManager resource (see the constructor for
     * why - a change here shouldn't force the whole API to reload). Synchronized so two threads
     * writing around the same time (e.g. two keys both getting an id assigned in quick succession)
     * can't interleave and corrupt the file - each still writes a full, self-consistent snapshot of
     * the live map, just not concurrently.
     */
    synchronized void persistWebhookKeys() {
        List<WebhookKeyStore.PersistedWebhookKey> entries = getWebhookKeys().values().stream()
                .map(webhookKey -> new WebhookKeyStore.PersistedWebhookKey(
                        webhookKey.getKey(),
                        webhookKey.getId(),
                        webhookKey.getTtl() != null ? webhookKey.getTtl().getTime() : null
                ))
                .collect(Collectors.toList());

        try {
            WebhookKeyStore.write(api.getGatewayContext().getSystemManager().getDataDir(), api.getName(), getName(), entries);
        } catch (Throwable t) {
            logger.error("Error persisting webhook keys", t);
        }
    }

    private String getServletPath(String key) {
        return Webhook.SERVLET_PATH + "/" + api.getName() + "/" + getName() + "/" + key;
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
                // false, not true: this can be called from inside the TTL future's own currently-
                // executing WebhookRunnable (if its check/add call triggers needsAuth() -> pause() ->
                // here for this same webhook), where interrupting would interrupt the calling thread
                // itself. See Schedule.shutdown() for the full explanation of this pattern.
                webhookKeyObj.getTtlFuture().cancel(false);
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
