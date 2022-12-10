package com.kyvislabs.api.client.gateway.pages;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.model.IgnitionWebApp;
import com.inductiveautomation.ignition.gateway.web.components.ConfigPanel;
import com.inductiveautomation.ignition.gateway.web.components.CsrfPreventingForm;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.authentication.OAuth2;
import com.kyvislabs.api.client.gateway.database.APIRecord;
import com.kyvislabs.api.client.gateway.managers.APIManager;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class APIOAuth2Panel extends ConfigPanel {

    private final Logger logger = LoggerFactory.getLogger("API.OAuth2.Panel");

    private APIRecord api;
    private OAuth2.GrantType grantType;
    private boolean pkce = false, twoFactor = false, captcha = false;
    private String twoFactorCode;
    private boolean captchaVisible = false;
    private String captchaCode;
    private byte[] captchaBytes;
    private IConfigPage configPage;

    public APIOAuth2Panel(final IConfigPage configPage, final APIManagerPage goBack, APIRecord api) {
        super("API.OAuth2.Title", configPage, goBack);
        this.api = api;

        String oauth2AuthUrl = "";
        String oauth2RedirectUrl = "";

        try {
            OAuth2 authType = (OAuth2) APIManager.get().getAPI(api.getId()).getAuthType().getAuthType();
            grantType = authType.getGrantType();
            pkce = authType.requiresPKCE();
            twoFactor = authType.requiresTwoFactor();
            captcha = authType.requiresCaptcha();
            if (grantType.equals(OAuth2.GrantType.AUTHORIZATIONCODE)) {
                oauth2AuthUrl = authType.getAuthorizationUrl();
                oauth2RedirectUrl = authType.getActualRedirectUrl();
            }
        } catch (Throwable ex) {
            logger.error("Error getting OAuth2 authorization URL");
        }

        WebMarkupContainer redirectURLSection = new WebMarkupContainer("redirectURL") {
            @Override
            public boolean isVisible() {
                return grantType != null && grantType.equals(OAuth2.GrantType.AUTHORIZATIONCODE) && !pkce;
            }
        };
        redirectURLSection.add(new Label("oauth2-redirect-url", Model.of(oauth2RedirectUrl)));
        add(redirectURLSection);

        WebMarkupContainer authLinkSection = new WebMarkupContainer("authLink") {
            @Override
            public boolean isVisible() {
                return grantType != null && grantType.equals(OAuth2.GrantType.AUTHORIZATIONCODE);
            }
        };
        authLinkSection.add(new ExternalLink("oauth2-authorize", Model.of(oauth2AuthUrl)) {
            @Override
            public boolean isVisible() {
                return !pkce;
            }
        });
        authLinkSection.add(new CsrfPreventingForm("form") {
            @Override
            protected void onSubmitInternal() {
                try {
                    OAuth2 authType = (OAuth2) APIManager.get().getAPI(api.getId()).getAuthType().getAuthType();
                    captchaBytes = authType.getAuthorizationPage();
                    if (captcha && captchaBytes != null) {
                        captchaVisible = true;
                    }
                    APIOAuth2Panel.this.configPage.setConfigPanel(APIOAuth2Panel.this.returnPanel);
                } catch (Throwable ex) {
                    logger.error("Error authorizing API", ex);
                }
            }

            @Override
            public boolean isVisible() {
                return pkce;
            }
        });
        add(authLinkSection);

        WebMarkupContainer captchaSection = new WebMarkupContainer("captcha") {
            @Override
            public boolean isVisible() {
                return captchaVisible;
            }
        };
        Form captchaForm = new CsrfPreventingForm("form") {
            @Override
            protected void onSubmitInternal() {
                if (captchaCode != null && !captchaCode.equals("")) {
                    try {
                        OAuth2 authType = (OAuth2) APIManager.get().getAPI(api.getId()).getAuthType().getAuthType();
                        authType.setCaptchaCode(captchaCode);
                        APIOAuth2Panel.this.configPage.setConfigPanel(APIOAuth2Panel.this.returnPanel);
                    } catch (Throwable ex) {
                        logger.error("Error saving captcha code", ex);
                    }
                }
            }
        };
        captchaForm.add(new InlineSVGImage("captchaImage", new PropertyModel<byte[]>(this, "captchaBytes")));
        captchaForm.add(new TextField<String>("captchaCode", new PropertyModel<String>(this, "captchaCode")));
        captchaSection.add(captchaForm);
        add(captchaSection);

        WebMarkupContainer twoFactorSection = new WebMarkupContainer("twoFactor") {
            @Override
            public boolean isVisible() {
                return twoFactor;
            }
        };
        Form twoFactorForm = new CsrfPreventingForm("form") {
            @Override
            protected void onSubmitInternal() {
                if (twoFactorCode != null && !twoFactorCode.equals("")) {
                    GatewayContext gatewayContext = ((IgnitionWebApp) getApplication()).getContext();

                    try {
                        API apiObj = APIManager.get().getAPI(api.getId());
                        apiObj.getVariables().setVariable(OAuth2.VARIABLE_2FA_CODE, twoFactorCode);
                        apiObj.getVariables().clearVariable(OAuth2.VARIABLE_2FA_CODE_WAITING);
                        gatewayContext.getPersistenceInterface().notifyRecordUpdated(api);
                        APIOAuth2Panel.this.configPage.setConfigPanel(APIOAuth2Panel.this.returnPanel);
                    } catch (Throwable ex) {
                        logger.error("Error saving 2FA code", ex);
                    }
                }
            }
        };
        twoFactorForm.add(new TextField<String>("twoFactorCode", new PropertyModel<String>(this, "twoFactorCode")));
        twoFactorSection.add(twoFactorForm);
        add(twoFactorSection);

        WebMarkupContainer twoFactorResetSection = new WebMarkupContainer("twoFactorReset") {
            @Override
            public boolean isVisible() {
                return twoFactor;
            }
        };
        twoFactorResetSection.add(new CsrfPreventingForm("form") {
            @Override
            protected void onSubmitInternal() {
                GatewayContext gatewayContext = ((IgnitionWebApp) getApplication()).getContext();

                try {
                    API apiObj = APIManager.get().getAPI(api.getId());
                    apiObj.getVariables().clearVariable(OAuth2.VARIABLE_2FA_CODE);
                    apiObj.getVariables().clearVariable(OAuth2.VARIABLE_2FA_CODE_WAITING);
                    gatewayContext.getPersistenceInterface().notifyRecordUpdated(api);
                } catch (Throwable ex) {
                    logger.error("Error resetting 2FA", ex);
                }
            }
        });
        add(twoFactorResetSection);
    }

    @Override
    public Pair<String, String> getMenuLocation() {
        return APIManagerPage.MENU_ENTRY.getMenuLocation();
    }

    @Override
    public IModel<String> getTitleModel() {
        return Model.of(BundleUtil.get().getString(getLocale(), "API.OAuth2.Title", api.getName()));
    }

}
