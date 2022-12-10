package com.kyvislabs.api.client.gateway.database;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import simpleorm.dataset.SFieldFlags;

public class APIVariableRecord extends PersistentRecord {
    public static final RecordMeta<APIVariableRecord> META = new RecordMeta<>(
            APIVariableRecord.class,
            "APIVariable"
    );

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final LongField APIId = new LongField(META, "APIId", SFieldFlags.SMANDATORY);
    public static final ReferenceField<APIRecord> API =
            new ReferenceField<APIRecord>(META, APIRecord.META, "API", APIId);
    public static final StringField Key = new StringField(META, "Key", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE).setIndexed(true);
    public static final EncodedStringField Value = new EncodedStringField(META, "Value");
    public static final BooleanField Required = new BooleanField(META, "Required").setDefault(true);
    public static final BooleanField Sensitive = new BooleanField(META, "Sensitive").setDefault(false);
    public static final BooleanField Hidden = new BooleanField(META, "Hidden").setDefault(false);

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public Long getId() {
        return getLong(Id);
    }

    public void setAPIId(long id) {
        setLong(APIId, id);
    }

    public String getKey() {
        return getString(Key);
    }

    public void setKey(String key) {
        setString(Key, key);
    }

    public String getValue() {
        return getString(Value);
    }

    public void setValue(String value) {
        setString(Value, value);
    }

    public boolean isRequired() {
        return getBoolean(Required);
    }

    public void setRequired(boolean required) {
        setBoolean(Required, required);
    }

    public boolean isSensitive() {
        return getBoolean(Sensitive);
    }

    public void setSensitive(boolean sensitive) {
        setBoolean(Sensitive, sensitive);
    }

    public boolean isHidden() {
        return getBoolean(Hidden);
    }

    public void setHidden(boolean hidden) {
        setBoolean(Hidden, hidden);
    }
}
