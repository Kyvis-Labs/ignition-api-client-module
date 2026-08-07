package com.kyvislabs.api.client.gateway.api.webhooks;

import com.kyvislabs.api.client.common.scripting.AbstractScriptFunctionsScriptModule;
import com.kyvislabs.api.client.gateway.GatewayHook;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Reads/writes webhook keys straight to a JSON file per webhook, deliberately outside
 * ConfigurationManager - webhook keys are runtime state tracked against an external system, not
 * config, and persisting one shouldn't force the owning API to reload (it's typically already
 * running, mid-execution more often than not). Same pattern StoreFileAction uses for saved files.
 *
 * Shared between Webhook (the normal runtime read/write path) and APIMigrationStrategy (which seeds
 * this store directly during an 8.1->8.3 migration).
 */
public class WebhookKeyStore {
    public record PersistedWebhookKey(String key, String id, Long ttl) {}

    public static File getFile(File dataDir, String apiName, String webhookName) {
        File moduleDir = new File(dataDir, "modules/" + AbstractScriptFunctionsScriptModule.MODULE_ID);
        File apiDir = new File(moduleDir, apiName);
        File webhooksDir = new File(apiDir, "webhooks");
        webhooksDir.mkdirs();
        return new File(webhooksDir, webhookName + ".json");
    }

    public static List<PersistedWebhookKey> read(File dataDir, String apiName, String webhookName) throws Exception {
        File file = getFile(dataDir, apiName, webhookName);
        if (!file.exists()) {
            return Collections.emptyList();
        }
        PersistedWebhookKey[] entries = GatewayHook.GSON.fromJson(Files.readString(file.toPath()), PersistedWebhookKey[].class);
        return entries != null ? List.of(entries) : Collections.emptyList();
    }

    public static void write(File dataDir, String apiName, String webhookName, List<PersistedWebhookKey> entries) throws Exception {
        Files.writeString(getFile(dataDir, apiName, webhookName).toPath(), GatewayHook.GSON.toJson(entries));
    }
}
