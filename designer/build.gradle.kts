plugins {
    `java-library`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.extra["sdk_version"]}")
    compileOnly("com.inductiveautomation.ignitionsdk:designer-api:${rootProject.extra["sdk_version"]}")
    compileOnly(projects.common)
}
