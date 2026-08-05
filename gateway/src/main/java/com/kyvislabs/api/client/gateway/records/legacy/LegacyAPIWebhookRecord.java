package com.kyvislabs.api.client.gateway.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.DateField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.LongField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;
import simpleorm.dataset.SFieldFlags;

import java.util.Date;

/**
 * Read-only mirror of the pre-8.3 "APIWebhook" PersistentRecord schema, kept solely so
 * {@link com.kyvislabs.api.client.gateway.records.APIMigrationStrategy} can query legacy rows.
 */
public class LegacyAPIWebhookRecord extends PersistentRecord {
    public static final RecordMeta<LegacyAPIWebhookRecord> META = new RecordMeta<>(
            LegacyAPIWebhookRecord.class,
            "APIWebhook"
    );

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final LongField APIId = new LongField(META, "APIId", SFieldFlags.SMANDATORY);
    public static final StringField Name = new StringField(META, "Name", SFieldFlags.SMANDATORY);
    public static final StringField Key = new StringField(META, "Key");
    public static final StringField UId = new StringField(META, "UId");
    public static final DateField TTL = new DateField(META, "TTL");

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

    public String getKey() {
        return getString(Key);
    }

    public String getUId() {
        return getString(UId);
    }

    public Date getTTL() {
        return getTimestamp(TTL);
    }
}
