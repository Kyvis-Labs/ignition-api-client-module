package com.kyvislabs.api.client.gateway.api.webhooks;

import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.gateway.tags.managed.WriteHandler;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WebhookKey implements VariableStore, WriteHandler {
    private Webhook webhook;
    private String key;
    private String id;
    private String url;
    private Date ttl;
    private ScheduledFuture ttlFuture;
    private Date lastCheck;
    private boolean exists;

    public WebhookKey(Webhook webhook, String key, String id, String url, Date ttl) {
        this.webhook = webhook;
        this.key = key;
        this.id = id;
        this.url = url;
        this.ttl = ttl;
        this.lastCheck = null;
        this.exists = false;

        if (key != null) {
            initTags();
            updateTags();
        }
    }

    public synchronized String getKey() {
        return key;
    }

    public synchronized String getId() {
        return id;
    }

    public synchronized void setId(String id) {
        this.id = id;
        webhook.persistWebhookKeys();
    }

    public synchronized String getUrl() {
        return url;
    }

    public synchronized Date getTtl() {
        return ttl;
    }

    public synchronized ScheduledFuture getTtlFuture() {
        return ttlFuture;
    }

    public synchronized void setTtlFuture(ScheduledFuture ttlFuture) {
        this.ttlFuture = ttlFuture;
    }

    public synchronized Date getLastCheck() {
        return lastCheck;
    }

    public synchronized void setLastCheck(Date lastCheck) {
        this.lastCheck = lastCheck;
    }

    public synchronized boolean isExists() {
        return exists;
    }

    public synchronized void setExists(boolean exists) {
        this.exists = exists;
    }

    public synchronized Long getTTLMs() {
        Date ttl = getTtl();
        if (ttl != null) {
            Date current = new Date();
            long ms = ttl.getTime() - current.getTime();
            if (ms < 0) {
                return null;
            } else {
                return ms;
            }
        }

        return null;
    }

    private String getTagPrefix() {
        return String.format("%s/Webhooks/%s/%s", webhook.getApi().getName(), webhook.getName(), getKey());
    }

    private void initTags() {
        webhook.getApi().getTagManager().configureTag(String.format("%s/Id", getTagPrefix()), DataType.String);
        webhook.getApi().getTagManager().configureTag(String.format("%s/URL", getTagPrefix()), DataType.String);
        webhook.getApi().getTagManager().configureTag(String.format("%s/TTL", getTagPrefix()), DataType.DateTime);
        webhook.getApi().getTagManager().configureTag(String.format("%s/Last Check", getTagPrefix()), DataType.DateTime);
        webhook.getApi().getTagManager().configureTag(String.format("%s/Exists", getTagPrefix()), DataType.Boolean);
        webhook.getApi().getTagManager().configureTag(String.format("%s/Remove", getTagPrefix()), DataType.Boolean, false);
        webhook.getApi().getTagManager().registerWriteHandler(String.format("%s/Remove", getTagPrefix()), this);
    }

    private void updateTags() {
        updateStatusTag("Id", getId());
        updateStatusTag("URL", getUrl());
        updateStatusTag("TTL", getTtl());
        updateStatusTag("Exists", isExists());
        updateStatusTag("Last Check", getLastCheck());
    }

    private void removeTags() {
        webhook.getApi().getTagManager().removeTag(String.format("%s/Id", getTagPrefix()));
        webhook.getApi().getTagManager().removeTag(String.format("%s/URL", getTagPrefix()));
        webhook.getApi().getTagManager().removeTag(String.format("%s/TTL", getTagPrefix()));
        webhook.getApi().getTagManager().removeTag(String.format("%s/Exists", getTagPrefix()));
        webhook.getApi().getTagManager().removeTag(String.format("%s/Last Check", getTagPrefix()));
        webhook.getApi().getTagManager().removeTag(String.format("%s/Remove", getTagPrefix()));
        webhook.getApi().getTagManager().removeTag(getTagPrefix());
    }

    public void updateStatusTag(String tag, Object value) {
        webhook.getApi().getTagManager().tagUpdate(String.format("%s/%s", getTagPrefix(), tag), value);
    }

    @Override
    public String getStoreName() {
        return "webhook";
    }

    @Override
    public synchronized Object getVariable(String name) throws APIException {
        if (name.equals("key")) {
            return getKey();
        } else if (name.equals("id")) {
            return getId();
        } else if (name.equals("name")) {
            return webhook.getName();
        } else if (name.equals("url")) {
            return getUrl();
        }

        throw new APIException("Variable '" + name + "' doesn't exist");
    }

    @Override
    public void setVariable(String name, Object value) {
        // no-op
    }

    // Only ever called for checkOnStart webhooks - see Webhook.init(), which deliberately never
    // calls this (or execute(VariableStore)) for a WebhookAction-created key. Uses `this` directly
    // (store name "webhook") since checkOnStart's check/add are written expecting the WebhookKey
    // itself, e.g. Google SDM's add body uses {{var::webhook.url}}.
    public void execute() {
        execute(this);
    }

    public void execute(VariableStore store) {
        webhook.getApi().getGatewayContext().getScheduledExecutorService().execute(new WebhookRunnable(store));
    }

    // Uses `this` directly (store name "webhook"), same as execute() above - see there for why. A
    // WebhookAction-created key's recheck therefore doesn't see whatever custom variables (or even
    // {{var::handler.xxx}} for the fixed fields) its owning function's action originally supplied;
    // that's fine since this only fires for a key that already has a ttl configured, which is the
    // checkOnStart case check/add are written for.
    public void schedule() {
        if (isExists()) {
            if (getTTLMs() != null) {
                if (getTtlFuture() != null) {
                    // false, not true: schedule() is called at the end of WebhookRunnable.run()
                    // itself, so getTtlFuture() here is often that same currently-executing task -
                    // interrupting it would interrupt the calling thread itself. See
                    // Schedule.shutdown() for the full explanation of this pattern.
                    getTtlFuture().cancel(false);
                }
                setTtlFuture(webhook.getApi().getGatewayContext().getScheduledExecutorService().schedule(new WebhookRunnable(this), getTTLMs(), TimeUnit.MILLISECONDS));
            }
        }
    }

    public void handleResponse(int statusCode, String contentType, String response) throws APIException {
        try {
            response = webhook.getHandle().getResponseFormat().format(this, response);
            webhook.getHandle().getActions().handleResponse(this, statusCode, contentType, response);
        } catch (Throwable t) {
            webhook.getHandle().setStatus(Function.FunctionStatus.FAILED);
            webhook.getLogger().error("Error handling webhook response", t);
            throw t;
        }

        webhook.getHandle().setStatus(Function.FunctionStatus.SUCCESS);
    }

    @Override
    public QualityCode write(TagPath tagPath, Object o) {
        updateStatusTag("Remove", false);

        // Dispatched async, not run inline - webhook.getRemove().callBlocking(this) is a network
        // call, and blocking here would tie up whatever (often limited-size) thread pool the tag
        // system uses to deliver writes, the same way TagWriteHandler.write() dispatches its
        // function calls via executeAsync() instead of running them inline. The write to this tag
        // itself always succeeds; removal happens in the background.
        webhook.getApi().getGatewayContext().getScheduledExecutorService().execute(this::remove);
        return QualityCode.Good;
    }

    private void remove() {
        boolean failed = false;

        Integer statusCode = webhook.getRemove().callBlocking(this);
        if (statusCode == null || statusCode < 200 || statusCode > 299) {
            webhook.getLogger().error(getKey() + ": Error calling webhook remove function");
            failed = true;
        }

        try {
            webhook.getWebhookKeys().remove(getKey());
            webhook.persistWebhookKeys();
        } catch (Throwable ex) {
            webhook.getLogger().error(getKey() + ": Error removing webhook from map", ex);
            failed = true;
        }

        try {
            removeTags();
        } catch (Throwable ex) {
            webhook.getLogger().error(getKey() + ": Error removing webhook tags", ex);
            failed = true;
        }

        if (failed) {
            webhook.getLogger().error(getKey() + ": Error removing webhook");
        }
    }

    public class WebhookRunnable implements Runnable {
        private VariableStore store;

        public WebhookRunnable(VariableStore store) {
            this.store = store;
        }

        @Override
        public void run() {
            try {
                boolean exists = false;

                Integer statusCode = webhook.getCheck().callBlocking(store);
                if (statusCode != null && statusCode >= 200 && statusCode <= 299) {
                    exists = true;
                }
                setLastCheck(new Date());

                if (!exists) {
                    statusCode = webhook.getAdd().callBlocking(store);
                    if (statusCode != null && statusCode >= 200 && statusCode <= 299) {
                        exists = true;
                    }

                    if (exists) {
                        try {
                            setId((String) webhook.getAdd().getVariable("id"));
                        } catch (Throwable ex) {
                            webhook.getLogger().debug("Error getting webhook id variable", ex);
                        }
                    }
                }

                setExists(exists);
                schedule();
                updateTags();
            } catch (Throwable ex) {
                webhook.getLogger().error(getKey() + ": Error checking webhook", ex);
            }
        }
    }
}
