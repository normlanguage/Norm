plugins {
    application
    alias(libs.plugins.graalvm.native)
    id("org.gradlex.extra-java-module-info") version "1.14.2"
}

description = "Norm command-line interface"

dependencies {
    implementation(project(":core"))
    implementation(libs.lsp4j)
    implementation(libs.gson)
}

extraJavaModuleInfo {
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j", "org.eclipse.lsp4j")
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc", "org.eclipse.lsp4j.jsonrpc")
}

application {
    applicationName = "norm"
    mainModule = "dev.w0fv1.norm.cli"
    mainClass = "dev.w0fv1.norm.cli.Main"
    applicationDefaultJvmArgs =
        listOf(
            "--sun-misc-unsafe-memory-access=allow",
            "--enable-native-access=org.graalvm.truffle",
        )
}

graalvmNative {
    toolchainDetection.set(true)
    binaries.named("main") {
        imageName.set("norm")
        mainClass.set(application.mainClass)
        sharedLibrary.set(false)
        buildArgs.add("--initialize-at-build-time=dev.w0fv1.norm.truffle.LanguageProvider")
        buildArgs.add("--enable-native-access=org.graalvm.truffle")
        resources.autodetect()
    }
}

val normVersion = project.version.toString()
val generatedVersionResources = layout.buildDirectory.dir("generated/version-resources")
val generateVersionProperties = tasks.register<WriteProperties>("generateVersionProperties") {
    destinationFile =
        generatedVersionResources.map { directory ->
            directory.file("dev/w0fv1/norm/cli/component/version.properties")
        }
    property("version", normVersion)
}

sourceSets {
    main {
        resources {
            srcDir(generatedVersionResources)
        }
    }
}

tasks.processResources {
    dependsOn(generateVersionProperties)
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.layout.projectDirectory)
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Norm CLI",
            "Implementation-Version" to project.version,
            "Main-Class" to application.mainClass.get(),
        )
    }
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        val script = windowsScript
        val content = script.readText()
        if (!content.startsWith("@echo off", ignoreCase = true)) {
            script.writeText("@echo off\r\n$content")
        }
    }
}
