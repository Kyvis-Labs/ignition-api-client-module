package com.kyvislabs.api.client.gateway.records;

import com.inductiveautomation.ignition.gateway.config.IdbMigrationStrategy;
import com.inductiveautomation.ignition.gateway.config.MigrationContext;
import com.inductiveautomation.ignition.gateway.secrets.SecretConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Migrates old PersistentRecord-based API configurations to the new ConfigurationManager resource system.
 *
 * Old schema:
 *   - APIRecord (Id, Name, Enabled, Configuration YAML)
 *   - APIVariableRecord (Id, APIId FK, Key, Value encrypted, Required, Sensitive, Hidden)
 *   - APICertificateRecord (Id, APIId FK, Certificate, PrivateKey)
 *
 * New schema: APIResource record (embedded variables + certificate)
 */
public class APIMigrationStrategy implements IdbMigrationStrategy {
    private final Logger logger = LoggerFactory.getLogger("API.Migration");

    @Override
    public void migrate(MigrationContext context) throws Exception {
        logger.info("Starting API Client migration from 8.1 PersistentRecords to 8.3 ConfigurationManager");

        try {
            // Query all old APIRecord rows via raw SQL since PersistentRecord classes are being removed
            List<OldAPIRecord> oldApis = context.queryLegacyRecords(
                    "SELECT Id, Name, Enabled, Configuration FROM API",
                    rs -> new OldAPIRecord(
                            rs.getLong("Id"),
                            rs.getString("Name"),
                            rs.getBoolean("Enabled"),
                            rs.getString("Configuration")
                    )
            );

            logger.info("Found " + oldApis.size() + " API records to migrate");

            for (OldAPIRecord oldApi : oldApis) {
                try {
                    migrateApi(context, oldApi);
                    logger.info("Migrated API: " + oldApi.name());
                } catch (Exception e) {
                    logger.error("Failed to migrate API: " + oldApi.name(), e);
                }
            }

            logger.info("API Client migration complete");
        } catch (Exception e) {
            logger.warn("Could not query legacy API records (table may not exist - fresh install): " + e.getMessage());
        }
    }

    private void migrateApi(MigrationContext context, OldAPIRecord oldApi) throws Exception {
        // Load variables for this API
        List<APIResource.APIVariable> variables = new ArrayList<>();
        try {
            List<OldAPIVariableRecord> oldVariables = context.queryLegacyRecords(
                    "SELECT Key, Value, Required, Sensitive, Hidden FROM APIVariable WHERE APIId = " + oldApi.id(),
                    rs -> new OldAPIVariableRecord(
                            rs.getString("Key"),
                            rs.getString("Value"),
                            rs.getBoolean("Required"),
                            rs.getBoolean("Sensitive"),
                            rs.getBoolean("Hidden")
                    )
            );

            for (OldAPIVariableRecord oldVar : oldVariables) {
                SecretConfig secretValue = oldVar.value() != null
                        ? SecretConfig.ofPlaintext(oldVar.value())
                        : SecretConfig.empty();
                variables.add(new APIResource.APIVariable(
                        oldVar.key(),
                        secretValue,
                        oldVar.required(),
                        oldVar.sensitive(),
                        oldVar.hidden()
                ));
            }
        } catch (Exception e) {
            logger.warn("Could not load variables for API " + oldApi.name() + ": " + e.getMessage());
        }

        // Load certificate for this API
        APIResource.APICertificate certificate = null;
        try {
            List<OldAPICertificateRecord> oldCerts = context.queryLegacyRecords(
                    "SELECT Certificate, PrivateKey FROM APICertificate WHERE APIId = " + oldApi.id(),
                    rs -> new OldAPICertificateRecord(
                            rs.getString("Certificate"),
                            rs.getString("PrivateKey")
                    )
            );

            if (!oldCerts.isEmpty()) {
                OldAPICertificateRecord oldCert = oldCerts.get(0);
                if (oldCert.certificate() != null && oldCert.privateKey() != null) {
                    certificate = new APIResource.APICertificate(
                            oldCert.certificate(),
                            SecretConfig.ofPlaintext(oldCert.privateKey())
                    );
                }
            }
        } catch (Exception e) {
            logger.warn("Could not load certificate for API " + oldApi.name() + ": " + e.getMessage());
        }

        // Build the new resource
        APIResource resource = new APIResource(
                oldApi.enabled(),
                oldApi.configuration() != null ? oldApi.configuration() : "",
                variables,
                certificate
        );

        // Write to new config system using the API name as the resource name
        context.writeResource(ResourceTypes.API_RESOURCE_TYPE, oldApi.name(), resource);
    }

    // Simple data holders for legacy records
    private record OldAPIRecord(long id, String name, boolean enabled, String configuration) {}
    private record OldAPIVariableRecord(String key, String value, boolean required, boolean sensitive, boolean hidden) {}
    private record OldAPICertificateRecord(String certificate, String privateKey) {}
}
