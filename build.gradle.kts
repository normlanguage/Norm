import org.gradle.api.tasks.Delete

plugins {
    base
    alias(libs.plugins.spotless) apply false
}

group = "dev.w0fv1.norm"
version = providers.gradleProperty("normVersion").orElse("0.0.0-dev").get()

layout.buildDirectory = layout.projectDirectory.dir("build")

subprojects {
    group = rootProject.group
    version = rootProject.version
    layout.buildDirectory = rootProject.layout.buildDirectory.dir(name)
}

tasks.register("printNormVersion") {
    doLast {
        println(project.version)
    }
}

tasks.register("qualityCheck") {
    dependsOn(":compiler:check", ":compiler:spotlessCheck",
        gradle.includedBuild("build-logic").task(":test"),
        gradle.includedBuild("build-logic").task(":spotlessCheck"))
}

tasks.named<Delete>("clean") {
    setDelete(providers.provider {
        layout.buildDirectory.get().asFile.listFiles()
            ?.filter { it.name != "build-logic" }
            .orEmpty()
    })
}
