package com.kyvislabs.api.client.gateway.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.BooleanField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.EncodedStringField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.LongField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;
import simpleorm.dataset.SFieldFlags;

/**
 * Read-only mirror of the pre-8.3 "APIVariable" PersistentRecord schema, kept solely so
 * {@link com.kyvislabs.api.client.gateway.records.APIMigrationStrategy} can query legacy rows
 * through SimpleORM (which transparently decodes {@code EncodedStringField} values).
 */
public class LegacyAPIVariableRecord extends PersistentRecord {
    public static final RecordMeta<LegacyAPIVariableRecord> META = new RecordMeta<>(
            LegacyAPIVariableRecord.class,
            "APIVariable"
    );

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final LongField APIId = new LongField(META, "APIId", SFieldFlags.SMANDATORY);
    public static final StringField Key = new StringField(META, "Key", SFieldFlags.SMANDATORY);
    public static final EncodedStringField Value = new EncodedStringField(META, "Value");
    public static final BooleanField Required = new BooleanField(META, "Required").setDefault(true);
    public static final BooleanField Sensitive = new BooleanField(META, "Sensitive").setDefault(false);
    public static final BooleanField Hidden = new BooleanField(META, "Hidden").setDefault(false);

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public long getId() {
        return getLong(Id);
    }

    public String getKey() {
        return getString(Key);
    }

    public String getValue() {
        return getString(Value);
    }

    public boolean isRequired() {
        return getBoolean(Required);
    }

    public boolean isSensitive() {
        return getBoolean(Sensitive);
    }

    public boolean isHidden() {
        return getBoolean(Hidden);
    }
}
