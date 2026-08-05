package com.kyvislabs.api.client.gateway.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.BooleanField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;
import simpleorm.dataset.SFieldFlags;

/**
 * Read-only mirror of the pre-8.3 "API" PersistentRecord schema, kept solely so
 * {@link com.kyvislabs.api.client.gateway.records.APIMigrationStrategy} can query legacy rows
 * through SimpleORM (which transparently decodes {@code EncodedStringField} values).
 */
public class LegacyAPIRecord extends PersistentRecord {
    public static final RecordMeta<LegacyAPIRecord> META = new RecordMeta<>(
            LegacyAPIRecord.class,
            "API"
    );

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final StringField Name = new StringField(META, "Name", SFieldFlags.SMANDATORY);
    public static final BooleanField Enabled = new BooleanField(META, "Enabled").setDefault(true);
    public static final StringField Configuration = new StringField(META, "Configuration", Integer.MAX_VALUE, SFieldFlags.SMANDATORY);

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public long getId() {
        return getLong(Id);
    }

    public String getName() {
        return getString(Name);
    }

    public boolean isEnabled() {
        return getBoolean(Enabled);
    }

    public String getConfiguration() {
        return getString(Configuration);
    }
}
