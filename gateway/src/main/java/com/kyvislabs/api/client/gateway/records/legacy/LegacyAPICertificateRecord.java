package com.kyvislabs.api.client.gateway.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.LongField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;
import simpleorm.dataset.SFieldFlags;

/**
 * Read-only mirror of the pre-8.3 "APICertificate" PersistentRecord schema, kept solely so
 * {@link com.kyvislabs.api.client.gateway.records.APIMigrationStrategy} can query legacy rows.
 */
public class LegacyAPICertificateRecord extends PersistentRecord {
    public static final RecordMeta<LegacyAPICertificateRecord> META = new RecordMeta<>(
            LegacyAPICertificateRecord.class,
            "APICertificate"
    );

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final LongField APIId = new LongField(META, "APIId", SFieldFlags.SMANDATORY);
    public static final StringField Certificate = new StringField(META, "Certificate", Integer.MAX_VALUE, SFieldFlags.SMANDATORY);
    public static final StringField PrivateKey = new StringField(META, "PrivateKey", Integer.MAX_VALUE, SFieldFlags.SMANDATORY);

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public long getId() {
        return getLong(Id);
    }

    public String getCertificate() {
        return getString(Certificate);
    }

    public String getPrivateKey() {
        return getString(PrivateKey);
    }
}
