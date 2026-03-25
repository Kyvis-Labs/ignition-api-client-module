import com.github.gradle.node.yarn.task.YarnTask

plugins {
    id("com.github.node-gradle.node") version "7.0.2"
}

node {
    version.set("18.20.8")
    yarnVersion.set("1.22.18")
    download.set(true)
}

val webpackTask = tasks.register<YarnTask>("webpack") {
    dependsOn(tasks.yarn)
    args.set(listOf("run", "build"))
    inputs.dir("src")
    inputs.files("package.json", "webpack.config.js", "tsconfig.json")
    outputs.dir("build/generated-resources")
}

tasks.register<Copy>("generateResources") {
    dependsOn(webpackTask)
    from("build/generated-resources")
    into("$buildDir/resources/main")
}

tasks.named("processResources") {
    dependsOn("generateResources")
}

sourceSets {
    main {
        resources {
            srcDir("build/generated-resources")
        }
    }
}
