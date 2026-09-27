pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "norm"
include(":compiler")
project(":compiler").projectDir = file("cli/compiler")
