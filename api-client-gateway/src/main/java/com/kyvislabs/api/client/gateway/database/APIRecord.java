package com.kyvislabs.api.client.gateway.database;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidationError;
import org.apache.wicket.validation.IValidator;
import org.yaml.snakeyaml.Yaml;
import simpleorm.dataset.SFieldFlags;
import simpleorm.utils.SException;

import java.util.Map;

public class APIRecord extends PersistentRecord {
    public static final RecordMeta<APIRecord> META = new RecordMeta<>(
            APIRecord.class,
            "API"
    ).setNounKey("API.Noun")
            .setNounPluralKey("API.Noun.Plural");

    public static final IdentityField Id = new IdentityField(META, "Id");
    public static final StringField Name = new StringField(META, "Name", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE).setIndexed(true).setUnique(true);
    public static final BooleanField Enabled = new BooleanField(META, "Enabled").setDefault(true);
    public static final StringField Configuration = new StringField(META, "Configuration", 2147483647, SFieldFlags.SMANDATORY).setMultiLine().addValidator(new IValidator() {
        @Override
        public void validate(IValidatable iValidatable) {
            String val = (String) iValidatable.getValue();

            if (val != null && !val.equals("")) {
                try {
                    Yaml yaml = new Yaml();
                    Map yamlMap = yaml.load(val);
                    if (yamlMap == null) {
                        iValidatable.error((IValidationError) iErrorMessageSource -> "Empty YAML");
                    }
                } catch (Throwable ex) {
                    iValidatable.error((IValidationError) iErrorMessageSource -> "Invalid or empty YAML");
                }
            } else {
                throw new SException.Validation("YAML cannot be NULL");
            }
        }
    });

    public static final Category SettingsCategory = new Category("API.PageTitle", 125).include(Name, Enabled, Configuration);

    static {
        Name.getFormMeta().setFieldNameKey("API.Name.Name");
        Name.getFormMeta().setFieldDescriptionKey("API.Name.Description");
        Enabled.getFormMeta().setFieldNameKey("API.Enabled.Name");
        Enabled.getFormMeta().setFieldDescriptionKey("API.Enabled.Description");
        Configuration.getFormMeta().setFieldNameKey("API.Configuration.Name");
        Configuration.getFormMeta().setFieldDescriptionKey("API.Configuration.Description");
    }

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public Long getId() {
        return getLong(Id);
    }

    public String getName() {
        return getString(Name);
    }

    public String getConfiguration() {
        return getString(Configuration);
    }

    public boolean isEnabled() {
        return getBoolean(Enabled);
    }
}