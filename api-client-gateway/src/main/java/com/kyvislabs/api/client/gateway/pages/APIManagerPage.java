package com.kyvislabs.api.client.gateway.pages;

import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistenceSession;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.web.components.ConfigPanel;
import com.inductiveautomation.ignition.gateway.web.components.ConfirmedTaskVetoException;
import com.inductiveautomation.ignition.gateway.web.components.actions.AbstractRecordInstanceAction;
import com.inductiveautomation.ignition.gateway.web.models.DefaultConfigTab;
import com.inductiveautomation.ignition.gateway.web.models.IConfigTab;
import com.inductiveautomation.ignition.gateway.web.models.LenientResourceModel;
import com.inductiveautomation.ignition.gateway.web.models.RecordModel;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import com.kyvislabs.api.client.gateway.GatewayHook;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.authentication.OAuth2;
import com.kyvislabs.api.client.gateway.database.APICertificateRecord;
import com.kyvislabs.api.client.gateway.database.APIRecord;
import com.kyvislabs.api.client.gateway.database.APIVariableRecord;
import com.kyvislabs.api.client.gateway.database.APIWebhookRecord;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import simpleorm.dataset.SQuery;

import java.util.ArrayList;
import java.util.List;

public class APIManagerPage extends APIRecordActionTable<APIRecord> {

    public static IConfigTab MENU_ENTRY = DefaultConfigTab.builder()
            .category(GatewayHook.CONFIG_CATEGORY)
            .name("apis")
            .i18n("API.API.MenuTitle")
            .page(APIManagerPage.class)
            .terms("api", "rest")
            .build();

    public APIManagerPage(IConfigPage configPage) {
        super(configPage);
    }

    @Override
    protected RecordMeta<APIRecord> getRecordMeta() {
        return APIRecord.META;
    }

    @Override
    public Pair<String, String> getMenuLocation() {
        return MENU_ENTRY.getMenuLocation();
    }

    @Override
    protected String getTitleKey() {
        return "API.PageTitle";
    }

    @Override
    protected List<ICalculatedField<APIRecord>> getCalculatedFields() {
        List<ICalculatedField<APIRecord>> calcFields = super.getCalculatedFields();
        if (calcFields == null) {
            calcFields = new ArrayList<>();
        }

        calcFields.add(new ICalculatedField<>() {
            @Override
            public String getFieldvalue(APIRecord record) {
                try {
                    return APIManager.get().getAPIStatus(record.getName());
                } catch (Throwable ignored) {
                    return "";
                }
            }

            @Override
            public String getHeaderKey() {
                return "API.Status.Name";
            }
        });

        calcFields.add(new ICalculatedField<>() {
            @Override
            public String getFieldvalue(APIRecord record) {
                try {
                    API api = APIManager.get().getAPI(record.getId());
                    String status = "";

                    if (api.getStatus().equals(API.APIStatus.DISABLED)) {
                        return API.APIStatus.DISABLED.getDisplay();
                    } else if (api.getStatus().equals(API.APIStatus.TRIAL_EXPIRED)) {
                        return API.APIStatus.TRIAL_EXPIRED.getDisplay();
                    } else {
                        status = api.getFunctions().getStatus();
                    }

                    return status;
                } catch (Throwable ignored) {
                    return "";
                }
            }

            @Override
            public String getHeaderKey() {
                return "API.Functions.Name";
            }
        });

        calcFields.add(new ICalculatedField<>() {
            @Override
            public String getFieldvalue(APIRecord record) {
                try {
                    API api = APIManager.get().getAPI(record.getId());
                    String status = "";

                    if (api.getStatus().equals(API.APIStatus.DISABLED)) {
                        return API.APIStatus.DISABLED.getDisplay();
                    } else if (api.getStatus().equals(API.APIStatus.TRIAL_EXPIRED)) {
                        return API.APIStatus.TRIAL_EXPIRED.getDisplay();
                    } else {
                        status = api.getWebhooks().getStatus();
                    }

                    return status;
                } catch (Throwable ignored) {
                    return "";
                }
            }

            @Override
            public String getHeaderKey() {
                return "API.Webhooks.Name";
            }
        });

        return calcFields;
    }

    @Override
    protected LoadableDetachableModel<Object> getCalculatedFieldModel(ICalculatedField<APIRecord> field, APIRecord record) {
        return new CalculatedFieldModel(field, record);
    }

    @Override
    protected void addRecordInstanceActions(RepeatingView view, APIRecord record) {
        super.addRecordInstanceActions(view, record);

        try {
            if (APIManager.get().getAPI(record.getId()).getAuthType().getAuthType() instanceof OAuth2) {
                view.add(new APIOAuth2Action(view.newChildId(), configPage, record));
            }
        } catch (Throwable ignored) {
            // no-op
        }

        try {
            if (APIManager.get().getAPI(record.getId()).isHttpsCertificates()) {
                view.add(new APICertificateAction(view.newChildId(), configPage, record));
            }
        } catch (Throwable ignored) {
            // no-op
        }

        view.add(new APIVariablesAction(view.newChildId(), configPage, record));
    }

    @Override
    protected String getNoRowsKey() {
        return "API.API.NoRows";
    }

    private class APIVariablesAction extends AbstractRecordInstanceAction<APIRecord> {

        public APIVariablesAction(String id, IConfigPage configPage, APIRecord record) {
            super(id, configPage, APIManagerPage.this, record);
        }

        @Override
        protected ConfigPanel createPanel(APIRecord record) {
            return new APIVariablePanel(configPage, APIManagerPage.this, record);
        }

        @Override
        protected String getCssClass() {
            return "variables";
        }

        @Override
        public IModel getLabel() {
            return new LenientResourceModel("API.Variable.Link");
        }
    }

    private class APIOAuth2Action extends AbstractRecordInstanceAction<APIRecord> {

        public APIOAuth2Action(String id, IConfigPage configPage, APIRecord record) {
            super(id, configPage, APIManagerPage.this, record);
        }

        @Override
        protected ConfigPanel createPanel(APIRecord record) {
            return new APIOAuth2Panel(configPage, APIManagerPage.this, record);
        }

        @Override
        protected String getCssClass() {
            return "oauth2";
        }

        @Override
        public IModel getLabel() {
            return new LenientResourceModel("API.OAuth2.Link");
        }
    }

    private class APICertificateAction extends AbstractRecordInstanceAction<APIRecord> {

        public APICertificateAction(String id, IConfigPage configPage, APIRecord record) {
            super(id, configPage, APIManagerPage.this, record);
        }

        @Override
        protected ConfigPanel createPanel(APIRecord record) {
            return new APICertificatesPanel(configPage, APIManagerPage.this, record);
        }

        @Override
        protected String getCssClass() {
            return "certificate";
        }

        @Override
        public IModel getLabel() {
            return new LenientResourceModel("API.Certificate.Link");
        }
    }

    @Override
    protected void canDelete(APIRecord record) throws ConfirmedTaskVetoException {

    }

    @Override
    protected void doDeleteRelatedRecords(PersistenceSession session, APIRecord record) {
        super.doDeleteRelatedRecords(session, record);

        SQuery<APIVariableRecord> query = new SQuery<>(APIVariableRecord.META);
        query.eq(APIVariableRecord.APIId, record.getId());
        List<APIVariableRecord> variables = session.query(query);
        for (APIVariableRecord variable : variables) {
            variable.deleteRecord();
        }

        SQuery<APIWebhookRecord> query2 = new SQuery<>(APIWebhookRecord.META);
        query2.eq(APIWebhookRecord.APIId, record.getId());
        List<APIWebhookRecord> webhooks = session.query(query2);
        for (APIWebhookRecord webhook : webhooks) {
            webhook.deleteRecord();
        }

        SQuery<APICertificateRecord> query3 = new SQuery<>(APICertificateRecord.META);
        query3.eq(APICertificateRecord.APIId, record.getId());
        APICertificateRecord certificateRecord = session.queryOne(query3);
        if (certificateRecord != null) {
            certificateRecord.deleteRecord();
        }
    }

    private class CalculatedFieldModel extends LoadableDetachableModel<Object> {

        ICalculatedField<APIRecord> field;
        RecordModel<APIRecord> model;

        public CalculatedFieldModel(ICalculatedField<APIRecord> field, APIRecord record) {
            this.field = field;
            this.model = new RecordModel<>(record);
        }

        @Override
        protected Object load() {
            try {
                APIRecord rec = model.getObject();
                String ret = null;
                if (rec != null) {
                    ret = getCalculatedFieldDisplayValue(field.getFieldvalue(rec));
                }
                return ret;
            } catch (Throwable ignored) {
                return "";
            }
        }

        @Override
        protected void onDetach() {
            model.detach();
        }
    }
}
