package com.kyvislabs.api.client.gateway.api.functions;

import com.kyvislabs.api.client.common.exceptions.APIException;
import com.kyvislabs.api.client.gateway.api.API;
import com.kyvislabs.api.client.gateway.api.interfaces.VariableStore;
import net.dongliu.requests.HttpHeaders;
import net.dongliu.requests.RawResponse;
import net.dongliu.requests.RequestBuilder;
import net.dongliu.requests.StatusCodes;
import net.dongliu.requests.exception.RequestsException;
import net.dongliu.requests.exception.TooManyRedirectsException;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class FunctionExecutor implements Runnable, Callable<Integer> {
    private Logger logger;
    private Function function;
    private VariableStore store;

    public FunctionExecutor(Logger logger, Function function, VariableStore store) {
        this.logger = logger;
        this.function = function;
        this.store = store;
    }

    private int _execute() throws APIException {
        API api = function.getApi();

        long setupStartTime = System.currentTimeMillis();
        if (function.getDepends() != null) {
            Function dependsFunction = api.getFunctions().getFunction(function.getDepends());
            if (function.isDependsAlways() || !dependsFunction.isHasExecuted()) {
                dependsFunction.executeBlocking(store);
            }
        }

        Map<String, Object> headers = api.getHeaders().getHeadersMap(store);
        headers.putAll(api.getAuthType().getHeadersMap());
        headers.putAll(function.getHeaders().getHeadersMap(store));
        headers.putAll(function.getBody().getHeadersMap());

        String url = function.getUrl().getValue(store);
        RequestBuilder builder = function.getApi().getRequestBuilder(url, function.getMethod());
        builder.headers(headers);
        List<net.dongliu.requests.Parameter<Object>> params = Parameter.getParameters(function.getParameters(), store);
        builder.params(params);
        String body = function.getBody().build(builder, store);

        if (function.isRedirectNoHeaders()) {
            builder.followRedirect(false);
        }

        long setupEndTime = System.currentTimeMillis();

        logger.debug(function.getApi().getName() + " request [method=" + function.getMethod().toString() + ", url=" + url + ", headers=" + headers.keySet().stream()
                .map(key -> key + "=" + headers.get(key).toString())
                .collect(Collectors.joining(", ", "{", "}")) + ", params=" + params.stream()
                .map(key -> key.name() + "=" + key.value().toString()).collect(Collectors.joining(", ", "{", "}")) + ", body=" + body + "]");

        long callStartTime = System.currentTimeMillis();

        RawResponse res = builder.send();
        int statusCode = res.statusCode();

        if (function.isRedirectNoHeaders() && isRedirect(statusCode)) {
            boolean found = false;
            int redirectTimes = 0;
            final int maxRedirectTimes = 5;
            while (redirectTimes++ < maxRedirectTimes) {
                String location = res.getHeader(HttpHeaders.NAME_LOCATION);
                if (location == null) {
                    throw new RequestsException("Redirect location not found");
                }

                Function.Method method = function.getMethod();
                if (statusCode == StatusCodes.MOVED_PERMANENTLY || statusCode == StatusCodes.FOUND || statusCode == StatusCodes.SEE_OTHER) {
                    method = Function.Method.GET;
                }

                builder = function.getApi().getRequestBuilder(location, method);
                if (statusCode == StatusCodes.MOVED_PERMANENTLY || statusCode == StatusCodes.FOUND || statusCode == StatusCodes.SEE_OTHER) {
                    body = null;
                } else {
                    body = function.getBody().build(builder, store);
                }
                builder.followRedirect(false);

                logger.debug(function.getApi().getName() + " redirect [method=" + method.toString() + ", url=" + location + ", body=" + body + "]");

                res = builder.send();
                statusCode = res.statusCode();
                if (!isRedirect(statusCode)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new TooManyRedirectsException(maxRedirectTimes);
            }
        }

        long callEndTime = System.currentTimeMillis();

        boolean success = statusCode >= 200 && statusCode <= 299;
        boolean error = statusCode >= 400;

        if (error && function.getAllowedErrorCodes().contains(statusCode)) {
            success = true;
            error = false;
        }

        String response = null;

        if (function.getResponseType().equals(Function.ResponseType.BYTES)) {
            response = Base64.encodeBase64String(res.readToBytes());
            logger.debug("Base64 encoding response");
        } else {
            response = res.readToText();
        }

        String contentType = null;
        try {
            contentType = res.getHeader("Content-Type");
        } catch (Throwable ex) {
            contentType = function.getResponseType().getContentType();
        }

        logger.debug(function.getApi().getName() + " response [statusCode=" + statusCode + ", contentType=" + contentType + ", response=" + response + "]");

        function.updateStatusTag("ResponseCode", statusCode);
        function.setStatus(success ? Function.FunctionStatus.SUCCESS : Function.FunctionStatus.FAILED);
        function.updateStatusTag("Response", error ? response : "");

        if (error) {
            logger.error("Error execution function: " + response);
        }

        long processStartTime = 0;
        long processEndTime = 0;
        if (success) {
            processStartTime = System.currentTimeMillis();
            response = function.getResponseFormat().format(store, response);
            function.getActions().handleResponse(store, statusCode, contentType, response);
            processEndTime = System.currentTimeMillis();
        }

        long setupTime = setupEndTime - setupStartTime;
        long callTime = callEndTime - callStartTime;
        long processTime = processEndTime - processStartTime;
        function.updateStatusTag("LastExecutionSetupDuration", setupTime);
        function.updateStatusTag("LastExecutionCallDuration", callTime);
        function.updateStatusTag("LastExecutionProcessDuration", processTime);

        function.setHasExecuted();

        return statusCode;
    }

    private static boolean isRedirect(int status) {
        return status == StatusCodes.MULTIPLE_CHOICES || status == StatusCodes.MOVED_PERMANENTLY || status == StatusCodes.FOUND || status == StatusCodes.SEE_OTHER
                || status == StatusCodes.TEMPORARY_REDIRECT || status == StatusCodes.PERMANENT_REDIRECT;
    }

    private Integer execute() {
        Integer ret = null;

        try {
            long functionStartTime = System.currentTimeMillis();
            API api = function.getApi();

            function.updateStatusTag("LastExecution", new Date());
            function.updateStatusTag("State", Function.State.RUNNING.getDisplay());

            try {
                if (!api.getAuthType().isAuthenticated()) {
                    api.getAuthType().authenticate(store);
                }

                // Handle an unauthorized response, login, and try again
                ret = _execute();
                if (ret == 401) {
                    api.getAuthType().authenticate(store);
                    ret = _execute();
                }
            } catch (Throwable ex) {
                logger.error("Error with request: " + ex.getMessage(), ex);
                function.setStatus(Function.FunctionStatus.FAILED);
                function.updateStatusTag("Response", ex.toString());
            }

            function.updateStatusTag("State", Function.State.PENDING.getDisplay());
            function.updateStatusTag("NextExecution", function.getNextExecution());

            long functionEndTime = System.currentTimeMillis();
            long functionTotalTime = functionEndTime - functionStartTime;
            function.updateStatusTag("LastExecutionDuration", functionTotalTime);
        } catch (Throwable ex) {
            logger.error("Error executing function: " + ex.getMessage(), ex);
        }

        return ret;
    }

    @Override
    public void run() {
        execute();
    }

    @Override
    public Integer call() {
        return execute();
    }
}
