package com.kyvislabs.api.client.gateway.records;

import com.inductiveautomation.ignition.gateway.dataroutes.openapi.annotations.Description;
import com.inductiveautomation.ignition.gateway.secrets.SecretConfig;

import java.util.List;

public record APIResource(
        @Description("Whether the API is enabled")
        boolean enabled,

        @Description("YAML configuration for this API")
        String configuration,

        @Description("API variables (credentials, tokens, etc.)")
        List<APIVariable> variables,

        @Description("Client SSL certificate for mutual TLS")
        APICertificate certificate
) {

    public record APIVariable(
            @Description("Variable key/name")
            String key,

            @Description("Encrypted variable value - populated only when sensitive is true")
            SecretConfig value,

            @Description("Plaintext variable value - populated only when sensitive is false")
            String plainValue,

            @Description("Whether this variable is required for the API to function")
            boolean required,

            @Description("Whether the variable value is sensitive (hidden in logs)")
            boolean sensitive,

            @Description("Whether the variable is hidden from scripting access")
            boolean hidden
    ) {}

    public record APICertificate(
            @Description("PEM-encoded client certificate")
            String certificate,

            @Description("PEM-encoded private key")
            SecretConfig privateKey
    ) {}
}
