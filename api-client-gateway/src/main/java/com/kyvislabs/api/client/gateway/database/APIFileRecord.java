package com.kyvislabs.api.client.gateway.database;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import simpleorm.dataset.SFieldFlags;

import java.util.Date;

public class APIFileRecord extends PersistentRecord {
    public static final RecordMeta<APIFileRecord> META = new RecordMeta<>(
            APIFileRecord.class,
            "APIFile"
    );

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final LongField APIId = new LongField(META, "APIId", SFieldFlags.SMANDATORY);
    public static final ReferenceField<APIRecord> API =
            new ReferenceField<APIRecord>(META, APIRecord.META, "API", APIId);
    public static final StringField FileId = new StringField(META, "FileId", SFieldFlags.SMANDATORY).setIndexed(true);
    public static final StringField FileName = new StringField(META, "FileName", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE).setIndexed(true);
    public static final StringField Extension = new StringField(META, "Extension", SFieldFlags.SMANDATORY);
    public static final StringField ContentType = new StringField(META, "ContentType", SFieldFlags.SMANDATORY);
    public static final StringField AccessToken = new StringField(META, "AccessToken", SFieldFlags.SMANDATORY).setIndexed(true);
    public static final DateField LastUpdate = new DateField(META, "LastUpdate");

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

    public String getFileId() {
        return getString(FileId);
    }

    public void setFileId(String fileId) {
        setString(FileId, fileId);
    }

    public String getFileName() {
        return getString(FileName);
    }

    public void setFileName(String fileName) {
        setString(FileName, fileName);
    }

    public String getContentType() {
        return getString(ContentType);
    }

    public void setContentType(String contentType) {
        setString(ContentType, contentType);
    }

    public String getExtension() {
        return getString(Extension);
    }

    public void setExtension(String extension) {
        setString(Extension, extension);
    }

    public String getAccessToken() {
        return getString(AccessToken);
    }

    public void setAccessToken(String accessToken) {
        setString(AccessToken, accessToken);
    }

    public Date getLastUpdate() {
        return getTimestamp(LastUpdate);
    }

    public void setLastUpdate(Date lastUpdate) {
        setTimestamp(LastUpdate, lastUpdate);
    }
}
