package com.kyvislabs.api.client.gateway.records;

import com.inductiveautomation.ignition.common.resourcecollection.ChangeOperation;
import com.inductiveautomation.ignition.common.resourcecollection.LastModification;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.inductiveautomation.ignition.gateway.config.migration.IdbMigrationStrategy;
import com.inductiveautomation.ignition.gateway.config.migration.MigrationContext;
import com.inductiveautomation.ignition.gateway.config.migration.MigrationException;
import com.inductiveautomation.ignition.gateway.config.migration.MigrationLog;
import com.kyvislabs.api.client.gateway.records.legacy.LegacyAPICertificateRecord;
import com.kyvislabs.api.client.gateway.records.legacy.LegacyAPIRecord;
import com.kyvislabs.api.client.gateway.records.legacy.LegacyAPIVariableRecord;
import com.inductiveautomation.ignition.gateway.secrets.Plaintext;
import com.inductiveautomation.ignition.gateway.secrets.SecretConfig;
import simpleorm.dataset.SQuery;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Migrates old PersistentRecord-based API configurations to the new ConfigurationManager resource system.
 *
 * Old schema:
 *   - API (Id, Name, Enabled, Configuration YAML)
 *   - APIVariable (Id, APIId FK, Key, Value encoded, Required, Sensitive, Hidden)
 *   - APICertificate (Id, APIId FK, Certificate, PrivateKey)
 *
 * New schema: APIResource record (embedded variables + certificate)
 */
public class APIMigrationStrategy implements IdbMigrationStrategy {

    @Override
    public List<SRecordMeta<? extends SRecordInstance>> getRecordMetas() {
        return List.of(LegacyAPIRecord.META, LegacyAPIVariableRecord.META, LegacyAPICertificateRecord.META);
    }

    @Override
    public MigrationResult migrate(MigrationContext context) throws MigrationException {
        MigrationLog log = context.getLog();
        log.strategyMessage("Starting API Client migration from 8.1 PersistentRecords to 8.3 ConfigurationManager");

        List<ChangeOperation> changeOperations = new ArrayList<>();

        List<LegacyAPIRecord> oldApis;
        try {
            oldApis = context.getPersistenceSession().query(new SQuery<>(LegacyAPIRecord.META));
        } catch (Exception e) {
            log.strategyMessage("Could not query legacy API records (table may not exist - fresh install): " + e.getMessage());
            return new MigrationResult(changeOperations, getTableNames());
        }

        log.strategyMessage("Found " + oldApis.size() + " API records to migrate");

        for (LegacyAPIRecord oldApi : oldApis) {
            try {
                changeOperations.add(migrateApi(context, oldApi));
                log.strategyMessage("Migrated API: " + oldApi.getName());
            } catch (Exception e) {
                log.strategyError("Failed to migrate API: " + oldApi.getName(), e);
            }
        }

        log.strategyMessage("API Client migration complete");
        return new MigrationResult(changeOperations, getTableNames());
    }

    private ChangeOperation migrateApi(MigrationContext context, LegacyAPIRecord oldApi) throws Exception {
        // Load variables for this API
        List<APIResource.APIVariable> variables = new ArrayList<>();
        List<LegacyAPIVariableRecord> oldVariables = context.getPersistenceSession().query(
                new SQuery<>(LegacyAPIVariableRecord.META).eq(LegacyAPIVariableRecord.APIId, oldApi.getId()));

        for (LegacyAPIVariableRecord oldVar : oldVariables) {
            variables.add(new APIResource.APIVariable(
                    oldVar.getKey(),
                    toSecretConfig(context, oldVar.getValue()),
                    oldVar.isRequired(),
                    oldVar.isSensitive(),
                    oldVar.isHidden()
            ));
        }

        // Load certificate for this API
        APIResource.APICertificate certificate = null;
        List<LegacyAPICertificateRecord> oldCerts = context.getPersistenceSession().query(
                new SQuery<>(LegacyAPICertificateRecord.META).eq(LegacyAPICertificateRecord.APIId, oldApi.getId()));

        if (!oldCerts.isEmpty()) {
            LegacyAPICertificateRecord oldCert = oldCerts.get(0);
            if (oldCert.getCertificate() != null && oldCert.getPrivateKey() != null) {
                certificate = new APIResource.APICertificate(
                        oldCert.getCertificate(),
                        toSecretConfig(context, oldCert.getPrivateKey())
                );
            }
        }

        // Build the new resource
        APIResource resource = new APIResource(
                oldApi.isEnabled(),
                oldApi.getConfiguration() != null ? oldApi.getConfiguration() : "",
                variables,
                certificate
        );

        ResourceBuilder builder = Resource.newBuilder()
                .setResourceCollectionName(ResourceTypes.API_RESOURCE_TYPE_META.getPreferredCollection())
                .setResourcePath(ResourceTypes.API_RESOURCE_TYPE_META.getPath(oldApi.getName()))
                .putAttribute("uuid", UUID.randomUUID().toString());
        ResourceTypes.API_RESOURCE_TYPE_META.getCodec().encode(resource, builder);

        Resource built = LastModification.update(builder.build(), MIGRATION_ACTOR);
        return ChangeOperation.newCreateOp(built);
    }

    private SecretConfig toSecretConfig(MigrationContext context, String plaintextValue) throws Exception {
        try (Plaintext plaintext = Plaintext.fromString(plaintextValue != null ? plaintextValue : "")) {
            return SecretConfig.embedded(context.getSystemEncryptionService().encryptToJson(plaintext));
        }
    }
}
