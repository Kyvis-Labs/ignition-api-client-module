package com.kyvislabs.api.client.gateway.database;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import simpleorm.dataset.SFieldFlags;

public class APICertificateRecord extends PersistentRecord {
    public static final RecordMeta<APICertificateRecord> META = new RecordMeta<>(
            APICertificateRecord.class,
            "APICertificate"
    );

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final LongField APIId = new LongField(META, "APIId", SFieldFlags.SMANDATORY);
    public static final ReferenceField<APIRecord> API =
            new ReferenceField<APIRecord>(META, APIRecord.META, "API", APIId);
    public static final StringField Certificate = new StringField(META, "Certificate", 2147483647, SFieldFlags.SMANDATORY);
    public static final StringField PrivateKey = new StringField(META, "PrivateKey", 2147483647, SFieldFlags.SMANDATORY);

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

    public String getCertificate() {
        return getString(Certificate);
    }

    public void setCertificate(String certificate) {
        setString(Certificate, certificate);
    }

    public String getPrivateKey() {
        return getString(PrivateKey);
    }

    public void setPrivateKey(String privateKey) {
        setString(PrivateKey, privateKey);
    }
}
