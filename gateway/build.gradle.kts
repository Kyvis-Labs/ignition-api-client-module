plugins {
    `java-library`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.extra["sdk_version"]}")
    compileOnly("com.inductiveautomation.ignitionsdk:gateway-api:${rootProject.extra["sdk_version"]}")
    compileOnly(projects.common)

    modlImplementation("org.yaml:snakeyaml:2.0")
    modlImplementation("com.jayway.jsonpath:json-path:2.8.0")
    modlImplementation("org.jsoup:jsoup:1.17.2")
    modlImplementation(projects.webUi)
}
