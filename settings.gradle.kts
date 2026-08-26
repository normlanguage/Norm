pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "norm"

include("core", "execution-api", "project-system", "truffle-backend", "cli")

project(":core").projectDir = file("tool/core")
project(":execution-api").projectDir = file("tool/execution-api")
project(":project-system").projectDir = file("tool/project-system")
project(":truffle-backend").projectDir = file("tool/truffle-backend")
project(":cli").projectDir = file("tool/cli/app")
