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

include("core", "cli")

project(":core").projectDir = file("tool/core")
project(":cli").projectDir = file("tool/cli/app")
