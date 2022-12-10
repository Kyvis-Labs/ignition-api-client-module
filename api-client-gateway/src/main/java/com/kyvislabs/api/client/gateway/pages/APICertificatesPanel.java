package com.kyvislabs.api.client.gateway.pages;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.model.IgnitionWebApp;
import com.inductiveautomation.ignition.gateway.web.components.ConfigPanel;
import com.inductiveautomation.ignition.gateway.web.components.CsrfPreventingForm;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import com.kyvislabs.api.client.gateway.database.APICertificateRecord;
import com.kyvislabs.api.client.gateway.database.APIRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

public class APICertificatesPanel extends ConfigPanel {

    private final Logger logger = LoggerFactory.getLogger("API.OAuth2.Panel");

    private APIRecord api;
    private APICertificateRecord certificateRecord;
    private String certificate = "", privateKey = "";

    public APICertificatesPanel(final IConfigPage configPage, final APIManagerPage goBack, APIRecord api) {
        super("API.OAuth2.Title", configPage, goBack);
        GatewayContext gatewayContext = ((IgnitionWebApp) getApplication()).getContext();
        this.api = api;

        SQuery<APICertificateRecord> query = new SQuery<>(APICertificateRecord.META);
        query.eq(APICertificateRecord.APIId, api.getId());
        certificateRecord = gatewayContext.getPersistenceInterface().queryOne(query);
        if (certificateRecord != null) {
            certificate = certificateRecord.getCertificate();
            privateKey = certificateRecord.getPrivateKey();
        }

        Form form = new CsrfPreventingForm("form") {
            @Override
            protected void onSubmitInternal() {
                GatewayContext gatewayContext = ((IgnitionWebApp) getApplication()).getContext();

                try {
                    if (certificateRecord == null) {
                        certificateRecord = gatewayContext.getPersistenceInterface().createNew(APICertificateRecord.META);
                        certificateRecord.setAPIId(api.getId());
                    }

                    certificateRecord.setCertificate(certificate);
                    certificateRecord.setPrivateKey(privateKey);
                    gatewayContext.getPersistenceInterface().save(certificateRecord);
                    gatewayContext.getPersistenceInterface().notifyRecordUpdated(api);
                } catch (Throwable ex) {
                    logger.error("Error saving certificate", ex);
                }
            }
        };

        form.add(new TextArea<String>("certificate", new PropertyModel<String>(this, "certificate")));
        form.add(new TextArea<String>("privateKey", new PropertyModel<String>(this, "privateKey")));
        add(form);
    }

    @Override
    public Pair<String, String> getMenuLocation() {
        return APIManagerPage.MENU_ENTRY.getMenuLocation();
    }

    @Override
    public IModel<String> getTitleModel() {
        return Model.of(BundleUtil.get().getString(getLocale(), "API.Certificate.Title", api.getName()));
    }

}
