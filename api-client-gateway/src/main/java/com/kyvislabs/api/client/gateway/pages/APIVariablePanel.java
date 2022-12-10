package com.kyvislabs.api.client.gateway.pages;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.model.IgnitionWebApp;
import com.inductiveautomation.ignition.gateway.web.components.ConfigPanel;
import com.inductiveautomation.ignition.gateway.web.components.CsrfPreventingForm;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import com.kyvislabs.api.client.gateway.database.APIRecord;
import com.kyvislabs.api.client.gateway.database.APIVariableRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class APIVariablePanel extends ConfigPanel {

    private final Logger logger = LoggerFactory.getLogger("API.Variable.Panel");

    private APIRecord api;
    private List<APIVariableRecord> variables, userVariables, apiVariables;
    private Map<String, Boolean> changeField;

    public APIVariablePanel(final IConfigPage configPage, final APIManagerPage goBack, APIRecord api) {
        super("API.Variable.Title", configPage, goBack);
        GatewayContext gatewayContext = ((IgnitionWebApp) getApplication()).getContext();
        this.api = api;

        changeField = new HashMap<>();
        userVariables = new ArrayList<>();
        apiVariables = new ArrayList<>();

        SQuery<APIVariableRecord> query = new SQuery<>(APIVariableRecord.META);
        query.eq(APIVariableRecord.APIId, api.getId());
        query.ascending(APIVariableRecord.Key);
        variables = gatewayContext.getPersistenceInterface().query(query);
        for (APIVariableRecord variable : variables) {
            if (variable.isRequired() && !variable.isHidden()) {
                userVariables.add(variable);
            } else {
                apiVariables.add(variable);
            }
        }

        @SuppressWarnings("unchecked")
        Form form = new CsrfPreventingForm("form") {
            @Override
            protected void onSubmitInternal() {
                GatewayContext gatewayContext = ((IgnitionWebApp) getApplication()).getContext();

                for (APIVariableRecord variable : variables) {
                    if (!variable.isSensitive() || (changeField.getOrDefault(variable.getKey(), false))) {
                        changeField.put(variable.getKey(), false);
                        gatewayContext.getPersistenceInterface().save(variable);
                    }
                }

                try {
                    gatewayContext.getPersistenceInterface().notifyRecordUpdated(api);
                } catch (Throwable ex) {
                    logger.error("Error saving variables", ex);
                }
            }
        };

        WebMarkupContainer userVariablesSection = new WebMarkupContainer("userVariables") {
            @Override
            public boolean isVisible() {
                return userVariables.size() > 0;
            }
        };

        userVariablesSection.add(new ListView("listview", userVariables) {
            protected void populateItem(ListItem item) {
                APIVariableRecord variable = (APIVariableRecord) item.getModelObject();
                item.add(new Label("key", Model.of(variable.getKey())));
                IModel<Boolean> checkboxModel = new IModel<Boolean>() {
                    @Override
                    public Boolean getObject() {
                        return changeField.getOrDefault(variable.getKey(), false);
                    }

                    @Override
                    public void setObject(Boolean value) {
                        changeField.put(variable.getKey(), value);
                    }

                    @Override
                    public void detach() {

                    }
                };
                PasswordTextField password = new PasswordTextField("valuePassword", new IModel<String>() {
                    @Override
                    public void detach() {

                    }

                    @Override
                    public String getObject() {
                        return variable.getValue();
                    }

                    @Override
                    public void setObject(String value) {
                        variable.setValue(value);
                    }
                }) {
                    @Override
                    public boolean isEnabled() {
                        return checkboxModel.getObject();
                    }

                    @Override
                    public boolean isVisible() {
                        return variable.isSensitive();
                    }
                };
                password.setOutputMarkupId(true);
                CheckBox passwordCheck = new AjaxCheckBox("passwordCheck", checkboxModel) {
                    @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        target.add(password);
                    }

                    @Override
                    public boolean isVisible() {
                        return variable.isSensitive();
                    }
                };
                item.add(passwordCheck);
                item.add(password);
                item.add(new TextField("valueText", new IModel<String>() {
                    @Override
                    public void detach() {

                    }

                    @Override
                    public String getObject() {
                        return variable.getValue();
                    }

                    @Override
                    public void setObject(String value) {
                        variable.setValue(value);
                    }
                }) {
                    @Override
                    public boolean isVisible() {
                        return !variable.isSensitive();
                    }
                });
            }
        });

        form.add(userVariablesSection);

        WebMarkupContainer apiVariablesSection = new WebMarkupContainer("apiVariables") {
            @Override
            public boolean isVisible() {
                return apiVariables.size() > 0;
            }
        };

        apiVariablesSection.add(new ListView("listview", apiVariables) {
            protected void populateItem(ListItem item) {
                APIVariableRecord variable = (APIVariableRecord) item.getModelObject();
                item.add(new Label("key", Model.of(variable.getKey())));
                item.add(new Label("valueLabel", new IModel<String>() {
                    @Override
                    public void detach() {

                    }

                    @Override
                    public String getObject() {
                        return variable.getValue();
                    }

                    @Override
                    public void setObject(String value) {

                    }
                }));
            }
        });

        form.add(apiVariablesSection);

        add(form);
    }

    @Override
    public Pair<String, String> getMenuLocation() {
        return APIManagerPage.MENU_ENTRY.getMenuLocation();
    }

    @Override
    public IModel<String> getTitleModel() {
        return Model.of(BundleUtil.get().getString(getLocale(), "API.Variable.Title", api.getName()));
    }

}
