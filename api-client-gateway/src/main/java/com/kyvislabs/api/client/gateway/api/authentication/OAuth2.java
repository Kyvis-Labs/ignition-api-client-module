package com.kyvislabs.api.client.gateway.api.authentication;

import com.inductiveautomation.ignition.common.gateway.HttpURL;
import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.Headers;
import com.kyvislabs.api.client.gateway.api.ValueString;
import com.kyvislabs.api.client.gateway.api.functions.Function;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import com.kyvislabs.api.client.gateway.managers.CertificateManager;
import net.dongliu.requests.*;
import net.dongliu.requests.utils.URLUtils;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class OAuth2 extends AbstractAuthType {
    public static final String AUTH_TYPE = "oauth2";
    public static final String VARIABLE_CLIENT_ID = "authType-oauth2-client-id";
    public static final String VARIABLE_CLIENT_SECRET = "authType-oauth2-client-secret";
    public static final String VARIABLE_USERNAME = "authType-oauth2-username";
    public static final String VARIABLE_PASSWORD = "authType-oauth2-password";
    public static final String VARIABLE_AUTHORIZATION_CODE = "authType-oauth2-auth-code";
    public static final String VARIABLE_ACCESS_TOKEN = "authType-oauth2-access-token";
    public static final String VARIABLE_TOKEN_TYPE = "authType-oauth2-token-type";
    public static final String VARIABLE_EXPIRATION = "authType-oauth2-expiration";
    public static final String VARIABLE_REFRESH_TOKEN = "authType-oauth2-refresh-token";
    public static final String VARIABLE_2FA_CODE = "authType-oauth2-2fa-code";
    public static final String VARIABLE_2FA_CODE_WAITING = "authType-oauth2-2fa-code-waiting";
    public static final String VARIABLE_BEARER_CLIENT_ID = "authType-oauth2-bearer-client-id";
    public static final String VARIABLE_BEARER_CLIENT_SECRET = "authType-oauth2-bearer-client-secret";
    public static final String VARIABLE_BEARER_ACCESS_TOKEN = "authType-oauth2-bearer-access-token";
    public static final String VARIABLE_PKCE_CODE_VERIFIER = "authType-oauth2-pkce-code-verifier";
    public static final String VARIABLE_PKCE_CODE_CHALLENGE = "authType-oauth2-pkce-code-challenge";

    private Logger logger;
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private ValueString authUrl;
    private ValueString accessTokenUrl;
    private String accessTokenKey;
    private String usernameInput, passwordInput;
    private ValueString bearerAccessTokenUrl;
    private ValueString redirectUrl;
    private ValueString captchaUrl;
    private Headers headers;
    private String scope;
    private GrantType grantType;
    private ValueString clientId;
    private ValueString clientSecret;
    private ValueString bearerClientId;
    private ValueString bearerClientSecret;
    private String bearerGrantType;
    private boolean twoFactor;
    private boolean captcha;
    private boolean pkce, authCode;
    private boolean randomUserAgent;
    private String userAgent = null;

    private Session session;
    private Map<String, Object> authBody;
    private List<Map.Entry<String, String>> authParams;
    private String authRedirectUrl;

    public OAuth2(API api) {
        super(api);
        this.logger = LoggerFactory.getLogger(String.format("API.%s.OAuth2", api.getName()));
        this.headers = new Headers(api);
    }

    @Override
    public void parse(Integer version, Map yamlMap) throws APIException {
        this.grantType = GrantType.valueOf(yamlMap.getOrDefault("grantType", "AUTHORIZATIONCODE").toString().toUpperCase());

        if (this.grantType.equals(GrantType.AUTHORIZATIONCODE) && !yamlMap.containsKey("authUrl")) {
            throw new APIException("OAuth2: Missing auth URL");
        }

        if (!yamlMap.containsKey("accessTokenUrl")) {
            throw new APIException("OAuth2: Missing access token URL");
        }

        if (!grantType.equals(GrantType.CLIENTCREDENTIALS) && !yamlMap.containsKey("scope")) {
            throw new APIException("OAuth2: Missing scope");
        }

        this.authUrl = ValueString.parseValueString(api, yamlMap, "authUrl", this.grantType.equals(GrantType.AUTHORIZATIONCODE));
        headers.parse(version, yamlMap);
        this.accessTokenUrl = ValueString.parseValueString(api, yamlMap, "accessTokenUrl", true);
        this.accessTokenKey = (String) yamlMap.getOrDefault("accessTokenKey", "access_token");
        this.usernameInput = (String) yamlMap.getOrDefault("usernameInput", "username");
        this.passwordInput = (String) yamlMap.getOrDefault("passwordInput", "password");
        this.bearerAccessTokenUrl = ValueString.parseValueString(api, yamlMap, "bearerAccessTokenUrl", false);
        this.redirectUrl = ValueString.parseValueString(api, yamlMap, "redirectUrl", false);
        this.captchaUrl = ValueString.parseValueString(api, yamlMap, "captchaUrl", false);
        this.scope = (String) yamlMap.get("scope");

        this.clientId = ValueString.parseValueString(api, yamlMap, "clientId");
        this.clientSecret = ValueString.parseValueString(api, yamlMap, "clientSecret");
        this.bearerClientId = ValueString.parseValueString(api, yamlMap, "bearerClientId");
        this.bearerClientSecret = ValueString.parseValueString(api, yamlMap, "bearerClientSecret");
        this.bearerGrantType = (String) yamlMap.getOrDefault("bearerGrantType", null);

        this.twoFactor = Boolean.valueOf(yamlMap.getOrDefault("2fa", "false").toString());
        this.captcha = Boolean.valueOf(yamlMap.getOrDefault("captcha", "false").toString());
        this.pkce = Boolean.valueOf(yamlMap.getOrDefault("pkce", "false").toString());
        this.authCode = Boolean.valueOf(yamlMap.getOrDefault("authCode", "false").toString());
        this.randomUserAgent = Boolean.valueOf(yamlMap.getOrDefault("randomUserAgent", "false").toString());
    }

    @Override
    public void initializeVariables() {
        if (getClientId() == null) {
            api.getVariables().setVariable(VARIABLE_CLIENT_ID, true, false, false);
        }

        if (getGrantType().equals(GrantType.PASSWORD) || requiresPKCE()) {
            api.getVariables().setVariable(VARIABLE_USERNAME, true, false, false);
            api.getVariables().setVariable(VARIABLE_PASSWORD, true, false, true);
        }

        if (!getGrantType().equals(GrantType.PASSWORD) && !requiresPKCE()) {
            if (getClientSecret() == null) {
                api.getVariables().setVariable(VARIABLE_CLIENT_SECRET, true, false, true);
            }
        }

        if (getGrantType().equals(GrantType.AUTHORIZATIONCODE)) {
            api.getVariables().setVariable(VARIABLE_AUTHORIZATION_CODE, false, true, true);

            if (requiresPKCE()) {
                api.getVariables().setVariable(VARIABLE_PKCE_CODE_VERIFIER, false, true, true);
                api.getVariables().setVariable(VARIABLE_PKCE_CODE_CHALLENGE, false, true, true);
            }

            if (requiresBearerAccessToken()) {
                if (getBearerClientId() == null) {
                    api.getVariables().setVariable(VARIABLE_BEARER_CLIENT_ID, true, false, false);
                }
                if (getBearerClientSecret() == null) {
                    api.getVariables().setVariable(VARIABLE_BEARER_CLIENT_SECRET, true, false, true);
                }
                api.getVariables().setVariable(VARIABLE_BEARER_ACCESS_TOKEN, false, true, true);
            }
        }

        if (requiresTwoFactor()) {
            api.getVariables().setVariable(VARIABLE_2FA_CODE, false, true, true);
            api.getVariables().setVariable(VARIABLE_2FA_CODE_WAITING, false, true, false);
        }

        api.getVariables().setVariable(VARIABLE_ACCESS_TOKEN, false, true, true);
        api.getVariables().setVariable(VARIABLE_TOKEN_TYPE, false, true, false);
        api.getVariables().setVariable(VARIABLE_EXPIRATION, false, true, false);
        api.getVariables().setVariable(VARIABLE_REFRESH_TOKEN, false, true, true);
    }

    public synchronized ValueString getAuthUrl() {
        return authUrl;
    }

    public synchronized ValueString getAccessTokenUrl() {
        return accessTokenUrl;
    }

    public synchronized String getAccessTokenKey() {
        return accessTokenKey;
    }

    public synchronized String getUsernameInput() {
        return usernameInput;
    }

    public synchronized String getPasswordInput() {
        return passwordInput;
    }

    public synchronized ValueString getRedirectUrl() {
        return redirectUrl;
    }

    public synchronized ValueString getCaptchaUrl() {
        return captchaUrl;
    }

    public synchronized ValueString getBearerAccessTokenUrl() {
        return bearerAccessTokenUrl;
    }

    public synchronized boolean requiresBearerAccessToken() {
        return getBearerAccessTokenUrl() != null;
    }

    public synchronized String getScope() {
        return scope;
    }

    public synchronized GrantType getGrantType() {
        return grantType;
    }

    public synchronized ValueString getClientId() {
        return clientId;
    }

    public String getActualClientId() throws APIException {
        if (getClientId() != null) {
            return getClientId().getValue();
        }
        return api.getVariables().getVariable(VARIABLE_CLIENT_ID);
    }

    public synchronized ValueString getClientSecret() {
        return clientSecret;
    }

    public String getActualClientSecret() throws APIException {
        if (getClientSecret() != null) {
            return getClientSecret().getValue();
        }

        try {
            return api.getVariables().getVariable(VARIABLE_CLIENT_SECRET);
        } catch (Throwable t) {

        }

        return null;
    }

    public synchronized ValueString getBearerClientId() {
        return bearerClientId;
    }

    public String getActualBearerClientId() throws APIException {
        if (getBearerClientId() != null) {
            return getBearerClientId().getValue();
        }
        return api.getVariables().getVariable(VARIABLE_BEARER_CLIENT_ID);
    }

    public synchronized ValueString getBearerClientSecret() {
        return bearerClientSecret;
    }

    public String getActualBearerClientSecret() throws APIException {
        if (getBearerClientSecret() != null) {
            return getBearerClientSecret().getValue();
        }
        return api.getVariables().getVariable(VARIABLE_BEARER_CLIENT_SECRET);
    }

    public synchronized String getBearerGrantType() {
        return bearerGrantType;
    }

    public synchronized boolean requiresTwoFactor() {
        return twoFactor;
    }

    public synchronized boolean requiresCaptcha() {
        return captcha;
    }

    public synchronized boolean isRandomUserAgent() {
        return randomUserAgent;
    }

    public synchronized boolean requiresPKCE() {
        return pkce;
    }

    public synchronized boolean requiresAuthCode() {
        return authCode;
    }

    public synchronized String getCodeVerifier() throws APIException {
        return api.getVariables().getVariable(VARIABLE_PKCE_CODE_VERIFIER);
    }

    public synchronized String getCodeChallenge() throws APIException {
        return api.getVariables().getVariable(VARIABLE_PKCE_CODE_CHALLENGE);
    }

    public synchronized Headers getHeaders() {
        return headers;
    }

    public String getAuthorizationUrl() throws APIException, MalformedURLException {
        String url = getAuthUrl().getValue();

        if (requiresPKCE()) {
            if (requiresAuthCode()) {
                setUserAgent(null);
                generateCodeChallenge();
                List<Map.Entry<String, String>> tmpParams = new ArrayList<>();
                tmpParams.add(Parameter.of("client_id", getActualClientId()));
                tmpParams.add(Parameter.of("code_challenge", getCodeChallenge()));
                tmpParams.add(Parameter.of("code_challenge_method", "S256"));
                tmpParams.add(Parameter.of("redirect_uri", getActualRedirectUrl()));
                tmpParams.add(Parameter.of("response_type", "code"));
                tmpParams.add(Parameter.of("scope", getScope()));
                tmpParams.add(Parameter.of("state", getRedirectState()));
                url = URLUtils.joinUrl(new URL(url), URLUtils.toStringParameters(tmpParams), Charset.defaultCharset()).toString();
            } else {
                return url;
            }
        } else {
            List<Map.Entry<String, String>> tmpParams = new ArrayList<>();
            tmpParams.add(Parameter.of("client_id", getActualClientId()));
            tmpParams.add(Parameter.of("redirect_uri", getActualRedirectUrl()));
            tmpParams.add(Parameter.of("response_type", "code"));
            tmpParams.add(Parameter.of("scope", getScope()));
            tmpParams.add(Parameter.of("state", getRedirectState()));
            tmpParams.add(Parameter.of("access_type", "offline"));
            tmpParams.add(Parameter.of("prompt", "consent"));
            url = URLUtils.joinUrl(new URL(url), URLUtils.toStringParameters(tmpParams), Charset.defaultCharset()).toString();
        }

        return url;
    }

    public String getActualRedirectUrl() throws APIException {
        if (getRedirectUrl() != null) {
            return getRedirectUrl().getValue();
        }

        HttpURL httpUrl = api.getGatewayContext().getRedundancyManager().getAllHttpAddresses().getMasterAddresses().get(0);
        httpUrl.setPath("/system/" + AUTH_TYPE);
        return httpUrl.toStringHTTPS().replace(":443", "");
    }

    public String getRedirectState() {
        return "?id=" + api.getId();
    }

    private Boolean hasExpired() throws APIException {
        String expirationDateStr = api.getVariables().getVariable(VARIABLE_EXPIRATION);
        try {
            if (expirationDateStr == null) {
                return null;
            } else {
                Date expirationDate = df.parse(expirationDateStr);
                logger.debug("Expiration date: " + expirationDateStr + " (" + expirationDate.after(new Date()) + ")");
                if (expirationDate.after(new Date())) {
                    return false;
                }
            }

            return true;
        } catch (Throwable ex) {
            throw new APIException("OAuth2: Error checking expiration date", ex);
        }
    }

    @Override
    public synchronized Map<String, Object> getHeadersMap() throws APIException {
        Map<String, Object> headersMap = new HashMap<>();
        if (requiresBearerAccessToken()) {
            headersMap.put("Authorization", "Bearer " + api.getVariables().getVariable(VARIABLE_BEARER_ACCESS_TOKEN));
        } else {
            headersMap.put("Authorization", api.getVariables().getVariable(VARIABLE_TOKEN_TYPE) + " " + api.getVariables().getVariable(VARIABLE_ACCESS_TOKEN));
        }
        return headersMap;
    }

    @Override
    public synchronized boolean isAuthorized() throws APIException {
        if (getGrantType().equals(GrantType.AUTHORIZATIONCODE)) {
            return api.getVariables().getVariable(VARIABLE_AUTHORIZATION_CODE) != null;
        }

        return true;
    }

    @Override
    public synchronized boolean isAuthenticated() throws APIException {
        Boolean hasExpired = hasExpired();
        if (getGrantType().equals(GrantType.AUTHORIZATIONCODE) && api.getVariables().getVariable(VARIABLE_AUTHORIZATION_CODE) == null) {
            throw new APIException("OAuth2: No authorization code");
        } else if (requiresTwoFactor() && api.getVariables().getVariable(VARIABLE_2FA_CODE_WAITING) != null) {
            api.setStatus(API.APIStatus.NEEDS_2FA_CODE);
            throw new APIException("OAuth2: Waiting for 2FA code");
        } else if (api.getVariables().getVariable(VARIABLE_ACCESS_TOKEN) == null) {
            return false;
        } else if (requiresBearerAccessToken() && api.getVariables().getVariable(VARIABLE_BEARER_ACCESS_TOKEN) == null) {
            return false;
        } else if (hasExpired == null || hasExpired) {
            return false;
        }
        return true;
    }

    private List<Map.Entry<String, String>> getParameters(Boolean refresh) throws APIException {
        List<Map.Entry<String, String>> parameters = new ArrayList<>();

        if (!getGrantType().equals(GrantType.CLIENTCREDENTIALS)) {
            parameters.add(Parameter.of("client_id", getActualClientId()));
        }

        if (getGrantType().equals(GrantType.PASSWORD)) {
            parameters.add(Parameter.of("scope", getScope()));
            parameters.add(Parameter.of("username", api.getVariables().getVariable(VARIABLE_USERNAME)));
            parameters.add(Parameter.of("password", api.getVariables().getVariable(VARIABLE_PASSWORD)));
        } else if (!getGrantType().equals(GrantType.CLIENTCREDENTIALS)) {
            if (getActualClientSecret() != null) {
                parameters.add(Parameter.of("client_secret", getActualClientSecret()));
            }
        }

        if (refresh != null && refresh && api.getVariables().getVariable(VARIABLE_REFRESH_TOKEN) != null) {
            parameters.add(Parameter.of("refresh_token", api.getVariables().getVariable(VARIABLE_REFRESH_TOKEN)));
            parameters.add(Parameter.of("grant_type", GrantType.REFRESH.getType()));
            parameters.add(Parameter.of("scope", getScope()));
        } else {
            parameters.add(Parameter.of("grant_type", getGrantType().getType()));

            if (getGrantType().equals(GrantType.AUTHORIZATIONCODE)) {
                parameters.add(Parameter.of("code", api.getVariables().getVariable(VARIABLE_AUTHORIZATION_CODE)));
                parameters.add(Parameter.of("redirect_uri", getActualRedirectUrl()));
                parameters.add(Parameter.of("scope", getScope()));
                parameters.add(Parameter.of("state", getRedirectState()));

                if (requiresPKCE()) {
                    parameters.add(Parameter.of("code_verifier", getCodeVerifier()));
                }
            }
        }

        return parameters;
    }

    private void needsAuth() {
        api.getVariables().clearVariable(OAuth2.VARIABLE_AUTHORIZATION_CODE);
        api.getVariables().clearVariable(OAuth2.VARIABLE_ACCESS_TOKEN);
        api.getVariables().clearVariable(OAuth2.VARIABLE_REFRESH_TOKEN);
        api.getVariables().clearVariable(OAuth2.VARIABLE_EXPIRATION);

        if (requiresBearerAccessToken()) {
            api.getVariables().clearVariable(OAuth2.VARIABLE_BEARER_ACCESS_TOKEN);
        }

        if (!api.getStatus().equals(API.APIStatus.NEEDS_2FA_CODE)) {
            api.setStatus(API.APIStatus.NEEDS_AUTHORIZATION);
            api.shutdown();
            api.getVariables().clearVariable(OAuth2.VARIABLE_2FA_CODE);
            api.getVariables().clearVariable(OAuth2.VARIABLE_2FA_CODE_WAITING);
        }
    }

    private void checkHeaders(RequestBuilder builder) {
        if (isRandomUserAgent()) {
            Map<String, Object> headers = new HashMap<>();
            checkHeaders(headers);
            builder.headers(headers);
        }
    }

    private void checkHeaders(Map<String, Object> headers) {
        if (isRandomUserAgent()) {
            if (userAgent == null) {
                userAgent = randomString(5);
            }
            headers.put("User-Agent", userAgent);
        }
    }

    private void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    private static String randomString(int length) {
        int low = 97; // a-z
        int high = 122; // A-Z
        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append((char) (low + (int) (random.nextFloat() * (high - low + 1))));
        }
        return sb.toString();
    }

    private void generateCodeChallenge() {
        try {
            String codeVerifier = CertificateManager.generateCodeVerifier();
            String codeChallenge = CertificateManager.generateCodeChallange(codeVerifier);
            api.getVariables().setVariable(VARIABLE_PKCE_CODE_VERIFIER, codeVerifier);
            api.getVariables().setVariable(VARIABLE_PKCE_CODE_CHALLENGE, codeChallenge);
        } catch (Throwable t) {
            logger.error("Error creating code verifier and challenge", t);
        }
    }

    private String getBaseUrl(RawResponse res, String url) throws Exception {
        if (url == null || url.equals("")) {
            return res.url();
        } else {
            URI uri = new URI(res.url());
            return uri.getScheme() + "://" + uri.getHost() + url;
        }
    }

    public byte[] getAuthorizationPage() throws Exception {
        setUserAgent(null);
        generateCodeChallenge();

        session = Requests.session();
        authRedirectUrl = getAuthorizationUrl();
        RequestBuilder builder = session.get(authRedirectUrl);
        authParams = new ArrayList<>();
        authParams.add(Parameter.of("client_id", getActualClientId()));
        authParams.add(Parameter.of("code_challenge", getCodeChallenge()));
        authParams.add(Parameter.of("code_challenge_method", "S256"));
        authParams.add(Parameter.of("redirect_uri", getActualRedirectUrl()));
        authParams.add(Parameter.of("response_type", "code"));
        authParams.add(Parameter.of("scope", getScope()));
        authParams.add(Parameter.of("state", getRedirectState()));
        builder.params(authParams);
        checkHeaders(builder);

        logger.debug(api.getName() + " request [method=" + Function.Method.GET.toString() + ", url=" + authRedirectUrl + ", body=none, params=" + authParams.stream()
                .map(key -> key.getKey() + "=" + key.getValue()).collect(Collectors.joining(", ", "{", "}")) + "]");

        RawResponse res = builder.send();
        boolean success = res.statusCode() >= 200 && res.statusCode() <= 299;
        String response = res.readToText();

        logger.debug(api.getName() + " response [statusCode=" + res.statusCode() + ", contentType=application/json" + ", response=" + response + ", cookies=" + res.cookies().stream().map(key -> key.name() + "=" + key.value()).collect(Collectors.joining(", ", "{", "}")) + "]");

        if (success) {
            authBody = new HashMap<>();
            Document jsoupDocument = Jsoup.parse(response);

            authRedirectUrl = getBaseUrl(res, jsoupDocument.selectFirst("form").attr("action"));

            Elements hiddenElements = jsoupDocument.select("input[type=hidden]");
            for (Element hiddenElement : hiddenElements) {
                authBody.put(hiddenElement.attr("name"), hiddenElement.attr("value"));
            }
            authBody.put(getUsernameInput(), api.getVariables().getVariable(VARIABLE_USERNAME));
            authBody.put(getPasswordInput(), api.getVariables().getVariable(VARIABLE_PASSWORD));

            builder = session.post(authRedirectUrl);
            builder.body(authBody);
            builder.followRedirect(false);
            checkHeaders(builder);

            logger.debug(api.getName() + " request [method=" + Function.Method.POST.toString() + ", url=" + authRedirectUrl + ", body=" + authBody.keySet().stream()
                    .map(key -> key + "=" + authBody.get(key)).collect(Collectors.joining(", ", "{", "}")) + "]");

            res = builder.send();
            success = res.statusCode() >= 200 && res.statusCode() <= 299;

            logger.debug(api.getName() + " response [statusCode=" + res.statusCode() + ", contentType=application/json" + ", response=" + res.readToText() + ", cookies=" + res.cookies().stream().map(key -> key.name() + "=" + key.value()).collect(Collectors.joining(", ", "{", "}")) + "]");

            if (success) {
                if (requiresCaptcha()) {
                    builder = session.get(getCaptchaUrl().getValue());
                    checkHeaders(builder);

                    logger.debug(api.getName() + " request [method=" + Function.Method.GET.toString() + ", url=" + getCaptchaUrl().getValue() + ", params=none, body=none]");

                    res = builder.send();
                    success = res.statusCode() >= 200 && res.statusCode() <= 299;

                    if (success) {
                        return res.readToBytes();
                    } else {
                        throw new APIException("OAuth2: Failed to get captcha image");
                    }
                } else {
                    getAuthorizationCode(res);
                    return null;
                }
            } else if (res.statusCode() == 302) {
                getAuthorizationCode(res);
                return null;
            } else {
                throw new APIException("OAuth2: Failed to get authorization code");
            }
        } else {
            throw new APIException("OAuth2: Failed to get login page");
        }
    }

    public void setAuthorizationCode(String authorizationCode) {
        try {
            api.getVariables().setVariable(OAuth2.VARIABLE_AUTHORIZATION_CODE, authorizationCode);
            api.getVariables().clearVariable(OAuth2.VARIABLE_ACCESS_TOKEN);
            api.getVariables().clearVariable(OAuth2.VARIABLE_REFRESH_TOKEN);
            api.getVariables().clearVariable(OAuth2.VARIABLE_EXPIRATION);
            api.getGatewayContext().getPersistenceInterface().notifyRecordUpdated(api.getRecord());
        } catch (Throwable t) {
            logger.error("Error setting authorization code", t);
        }
    }

    public void setCaptchaCode(String captchaCode) {
        try {
            authBody.put("captcha", captchaCode);
            RequestBuilder builder = session.post(authRedirectUrl);
            builder.params(authParams);
            builder.body(authBody);
            builder.followRedirect(false);
            checkHeaders(builder);

            logger.debug(api.getName() + " request [method=" + Function.Method.POST.toString() + ", url=" + authRedirectUrl + ", params=" + authParams.stream()
                    .map(key -> key.getKey() + "=" + key.getValue()).collect(Collectors.joining(", ", "{", "}")) + ", body=" + authBody.keySet().stream()
                    .map(key -> key + "=" + authBody.get(key)).collect(Collectors.joining(", ", "{", "}")) + "]");

            RawResponse res = builder.send();

            logger.debug(api.getName() + " response [statusCode=" + res.statusCode() + ", contentType=application/json" + ", response=" + res.readToText() + "]");

            getAuthorizationCode(res);
        } catch (Throwable t) {
            logger.error("Error setting captcha code", t);
        }
    }

    private Map<String, String> parseLocationQuery(String location) throws URISyntaxException {
        URI uri = new URI(location);
        return Arrays.stream(uri.getQuery().split("&")).map(str -> str.split("="))
                .collect(Collectors.toMap(str -> str[0], str -> str[1]));
    }

    public void getAuthorizationCode(RawResponse res) {
        try {
            String location = null;
            while (res.statusCode() == 302) {
                location = res.getHeader("Location");
                if (location != null && location.indexOf(getActualRedirectUrl()) != 0) {
                    location = getBaseUrl(res, location);

                    RequestBuilder builder = session.get(location);
                    builder.followRedirect(false);
                    checkHeaders(builder);

                    logger.debug(api.getName() + " request [method=" + Function.Method.GET.toString() + ", url=" + location + ", params=none, body=none]");
                    res = builder.send();
                    logger.debug(api.getName() + " response [statusCode=" + res.statusCode() + ", contentType=application/json" + ", response=" + res.readToText() + "]");
                } else {
                    break;
                }
            }

            Map<String, String> params = parseLocationQuery(location);
            if (params.containsKey("code")) {
                String code = params.get("code");
                logger.debug("Found code: " + code);
                setAuthorizationCode(code);
            } else {
                logger.error("Couldn't find code in Location header: " + location);
            }
        } catch (Throwable t) {
            logger.error("Error getting authorization code", t);
        }
    }

    private void getBearerAccessToken(VariableStore store) throws Exception {
        String url = getBearerAccessTokenUrl().getValue(store);
        RequestBuilder builder = session != null ? session.post(url) : api.getRequestBuilder(url, Function.Method.POST);

        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + api.getVariables().getVariable(VARIABLE_ACCESS_TOKEN));
        checkHeaders(headers);
        builder.headers(headers);

        List<Map.Entry<String, String>> params = new ArrayList<>();
        params.add(Parameter.of("grant_type", getBearerGrantType()));
        params.add(Parameter.of("client_id", getActualBearerClientId()));
        params.add(Parameter.of("client_secret", getActualBearerClientSecret()));
        builder.body(params);

        logger.debug(api.getName() + " request [method=" + Function.Method.POST.toString() + ", url=" + url + ", headers=" + headers.keySet().stream()
                .map(key -> key + "=" + headers.get(key).toString())
                .collect(Collectors.joining(", ", "{", "}")) + ",params=none, body=" + params.stream()
                .map(key -> key.getKey() + "=" + key.getValue()).collect(Collectors.joining(", ", "{", "}")) + "]");

        RawResponse res = builder.send();
        boolean success = res.statusCode() >= 200 && res.statusCode() <= 299;
        String response = res.readToText();

        logger.debug(api.getName() + " response [statusCode=" + res.statusCode() + ", contentType=application/json" + ", response=" + response + "]");

        if (success) {
            JSONObject responseObj = new JSONObject(response);
            String accessToken = responseObj.getString("access_token");
            api.getVariables().setVariable(VARIABLE_BEARER_ACCESS_TOKEN, accessToken);
        } else {
            throw new APIException("OAuth2: Failed to get bearer access token");
        }
    }

    @Override
    public void authenticate(VariableStore store) throws APIException {
        String url = getAccessTokenUrl().getValue(store);
        RequestBuilder builder = session != null ? session.post(url) : api.getRequestBuilder(url, Function.Method.POST);
        List<Map.Entry<String, String>> params = getParameters(hasExpired());

        Map<String, Object> headers = getHeaders().getHeadersMap(store);

        if (getGrantType().equals(GrantType.CLIENTCREDENTIALS)) {
            String auth = getActualClientId() + ":" + getActualClientSecret();
            headers.put("Authorization", "Basic " + new String(Base64.encodeBase64(auth.getBytes())));
        }

        boolean needs2FA = false;
        if (requiresTwoFactor()) {
            if (api.getVariables().getVariable(VARIABLE_2FA_CODE) != null) {
                headers.put("2fa-support", true);
                headers.put("2fa-code", api.getVariables().getVariable(VARIABLE_2FA_CODE));
                needs2FA = false;
            } else {
                needs2FA = true;
            }
        }

        checkHeaders(headers);
        builder.headers(headers);
        builder.body(params);

        logger.debug(api.getName() + " request [method=" + Function.Method.POST.toString() + ", url=" + url + ", headers=" + headers.keySet().stream()
                .map(key -> key + "=" + headers.get(key).toString())
                .collect(Collectors.joining(", ", "{", "}")) + ", params=none, body=" + params.stream()
                .map(key -> key.getKey() + "=" + key.getValue()).collect(Collectors.joining(", ", "{", "}")) + "]");

        RawResponse res = builder.send();

        boolean success = res.statusCode() >= 200 && res.statusCode() <= 299;
        String response = res.readToText();

        logger.debug(api.getName() + " response [statusCode=" + res.statusCode() + ", contentType=application/json" + ", response=" + response + "]");

        JSONObject responseObj = null;

        try {
            if (success) {
                responseObj = new JSONObject(response);
                String accessToken = responseObj.getString(getAccessTokenKey());
                String tokenType = responseObj.getString("token_type");
                int expiresIn = responseObj.getInt("expires_in");
                String refreshToken = null;

                if (responseObj.has("refresh_token")) {
                    refreshToken = responseObj.getString("refresh_token");
                }

                Calendar cal = Calendar.getInstance();
                cal.setTime(new Date());
                cal.add(Calendar.SECOND, expiresIn);
                String expiration = df.format(cal.getTime());

                api.getVariables().setVariable(VARIABLE_ACCESS_TOKEN, accessToken);
                api.getVariables().setVariable(VARIABLE_TOKEN_TYPE, tokenType);
                api.getVariables().setVariable(VARIABLE_EXPIRATION, expiration);

                if (responseObj.has("refresh_token")) {
                    api.getVariables().setVariable(VARIABLE_REFRESH_TOKEN, refreshToken);
                }

                api.getVariables().clearVariable(VARIABLE_2FA_CODE);

                if (requiresBearerAccessToken()) {
                    getBearerAccessToken(store);
                }
            } else {
                if (res.statusCode() == 412 && needs2FA) {
                    api.getVariables().setVariable(VARIABLE_2FA_CODE_WAITING, "yes");
                    api.setStatus(API.APIStatus.NEEDS_2FA_CODE);
                    throw new APIException("OAuth2: Waiting for 2FA code");
                }

                needsAuth();
                throw new APIException("OAuth2: Need to authorize");
            }
        } catch (Throwable ex) {
            needsAuth();
            throw new APIException("OAuth2: Error handling response, try to authorize again", ex);
        }
    }

    public enum GrantType {
        AUTHORIZATIONCODE("authorization_code"),
        CLIENTCREDENTIALS("client_credentials"),
        PASSWORD("password"),
        REFRESH("refresh_token");

        private String type;

        GrantType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }
}
