package com.kyvislabs.api.client.gateway.database;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import simpleorm.dataset.SFieldFlags;

import java.util.Date;

public class APIWebhookRecord extends PersistentRecord {
    public static final RecordMeta<APIWebhookRecord> META = new RecordMeta<>(
            APIWebhookRecord.class,
            "APIWebhook"
    );

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final LongField APIId = new LongField(META, "APIId", SFieldFlags.SMANDATORY);
    public static final ReferenceField<APIRecord> API =
            new ReferenceField<APIRecord>(META, APIRecord.META, "API", APIId);
    public static final StringField Name = new StringField(META, "Name", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE).setIndexed(true);
    public static final StringField Key = new StringField(META, "Key");
    public static final StringField UId = new StringField(META, "UId");
    public static final StringField Url = new StringField(META, "Url");
    public static final DateField TTL = new DateField(META, "TTL");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public Long getId() {
        return getLong(Id);
    }

    public void setApiId(long apiId) {
        setLong(APIId, apiId);
    }

    public String getName() {
        return getString(Name);
    }

    public void setName(String name) {
        setString(Name, name);
    }

    public String getKey() {
        return getString(Key);
    }

    public void setKey(String key) {
        setString(Key, key);
    }

    public String getUId() {
        return getString(UId);
    }

    public void setUId(String uId) {
        setString(UId, uId);
    }

    public String getUrl() {
        return getString(Url);
    }

    public void setUrl(String url) {
        setString(Url, url);
    }

    public Date getTTL() {
        return getTimestamp(TTL);
    }

    public void setTTL(Date ttl) {
        setTimestamp(TTL, ttl);
    }
}
