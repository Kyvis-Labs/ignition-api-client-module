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

    modlImplementation("net.dongliu:commons:8.1.0")
    modlImplementation("com.fasterxml.jackson.core:jackson-databind:2.10.0")
    modlImplementation("com.alibaba:fastjson:1.2.51")
    modlImplementation("org.yaml:snakeyaml:2.0")
    modlImplementation("com.jayway.jsonpath:json-path:2.8.0")
    modlImplementation("org.jsoup:jsoup:1.17.2")
    modlImplementation(projects.webUi)
}
