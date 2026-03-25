import io.ia.sdk.gradle.modl.api.permission.PermissionConfig

plugins {
    id("io.ia.sdk.modl") version "0.3.0"
}

val sdk_version: String by extra { "8.3.0" }

version = "2.0.0.${project.findProperty("buildTimestamp") ?: "SNAPSHOT"}"

ignitionModule {
    name.set("API Client Module")
    fileName.set("api-client-${version}.modl")
    id.set("com.kyvislabs.api.client")
    moduleVersion.set(version as String)
    license.set(file("LICENSE"))
    moduleDependencies.set(mapOf<String, String>())
    requiredIgnitionVersion.set("8.3.0")
    skipModlSigning.set(true)

    projectScopes.putAll(mapOf(
        ":common" to "GCD",
        ":client" to "C",
        ":designer" to "D",
        ":gateway" to "G",
        ":web-ui" to "G"
    ))

    hooks.putAll(mapOf(
        "com.kyvislabs.api.client.gateway.GatewayHook" to "G",
        "com.kyvislabs.api.client.client.ClientHook" to "C",
        "com.kyvislabs.api.client.designer.DesignerHook" to "D"
    ))
}
