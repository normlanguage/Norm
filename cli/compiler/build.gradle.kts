import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    application
    id("org.gradlex.extra-java-module-info") version "1.14.2"
}

description = "Norm compiler"

val resolverProviderModule = "org.apache.maven:maven-resolver-provider"
val resolverProviderMergedModules = listOf(
    "org.apache.maven:maven-model-builder",
    "org.apache.maven:maven-model",
    "org.apache.maven:maven-repository-metadata",
    "org.apache.maven:maven-artifact",
    "org.apache.maven:maven-builder-support",
)

val nativeExecution = configurations.create("nativeExecution") {
    isCanBeResolved = false
    isCanBeConsumed = false
}
val nativeExecutionRuntime = configurations.create("nativeExecutionRuntime") {
    isCanBeResolved = false
    isCanBeConsumed = false
}
val nativeHosted = configurations.create("nativeHosted") {
    isCanBeResolved = false
    isCanBeConsumed = false
}
configurations.implementation { extendsFrom(nativeExecution, nativeHosted) }
configurations.runtimeOnly { extendsFrom(nativeExecutionRuntime) }

abstract class GenerateToolchainArtifacts : DefaultTask() {
    @get:Input
    abstract val identities: MapProperty<String, String>

    @get:Input
    abstract val roots: ListProperty<String>

    @get:Input
    abstract val dependencies: MapProperty<String, List<String>>

    @get:Input
    abstract val purposeRoots: MapProperty<String, List<String>>

    @get:Input
    abstract val mergedModules: MapProperty<String, List<String>>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifacts: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val files = artifacts.files.associateBy { it.name }
        check(files.size == artifacts.files.size) { "Duplicate toolchain artifact filenames" }
        check(files.keys == identities.get().keys) { "Toolchain artifact identities do not match files" }
        val input = temporaryDir.resolve("catalog-input.json")
        input.writeText(groovy.json.JsonOutput.toJson(mapOf(
            "artifacts" to identities.get().toSortedMap().map { (name, identity) ->
                mapOf("path" to files.getValue(name).absolutePath, "coordinate" to identity)
            },
            "roots" to roots.get(),
            "dependencies" to dependencies.get(),
            "purposes" to purposeRoots.get(),
            "mergedModules" to mergedModules.get(),
        )))
        execOperations.javaexec {
            executable(javaLauncher.get().executablePath.asFile)
            classpath(generatorClasspath)
            mainClass.set("dev.w0fv1.norm.packaging.ToolchainArtifactCatalogGenerator")
            args(input.absolutePath, outputDirectory.file("toolchain-artifacts.json").get().asFile.absolutePath)
        }.assertNormalExitValue()
    }
}

abstract class GenerateBuiltinAbi : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val schemaFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        execOperations.javaexec {
            executable(javaLauncher.get().executablePath.asFile)
            classpath(generatorClasspath)
            mainClass.set("dev.w0fv1.norm.codegen.BuiltinAbiGenerator")
            args(schemaFile.get().asFile.absolutePath, outputDirectory.get().asFile.absolutePath)
        }.assertNormalExitValue()
    }
}

abstract class GenerateRuntimeLaunchers : DefaultTask() {
    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val entrypointClass: Property<String>

    @get:Input
    abstract val launcherJvmArguments: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        execOperations.javaexec {
            executable(javaLauncher.get().executablePath.asFile)
            classpath(generatorClasspath)
            mainClass.set("dev.w0fv1.norm.packaging.RuntimeLauncherGenerator")
            args("bundled", outputDirectory.get().asFile.absolutePath, moduleName.get(), entrypointClass.get())
            args(launcherJvmArguments.get())
        }.assertNormalExitValue()
    }
}

abstract class PublishNativeLauncher : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val projectFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val applicationIcon: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimePayload: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimePayloadDigest: RegularFileProperty

    @get:Input
    abstract val normVersion: Property<String>

    @get:OutputFile
    abstract val outputExecutable: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun publish() {
        check(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "The native Norm launcher can only be built on Windows"
        }
        val output = outputExecutable.get().asFile
        output.parentFile.deleteRecursively()
        output.parentFile.mkdirs()
        execOperations.exec {
            executable("dotnet")
            args(
                "publish",
                projectFile.get().asFile.absolutePath,
                "--configuration",
                "Release",
                "--runtime",
                "win-x64",
                "--self-contained",
                "true",
                "--output",
                output.parentFile.absolutePath,
                "-p:NormVersion=${normVersion.get()}",
                "-p:NormPayloadPath=${runtimePayload.get().asFile.absolutePath}",
                "-p:NormPayloadDigestPath=${runtimePayloadDigest.get().asFile.absolutePath}",
            )
        }
        check(output.isFile) {
            "The native Norm launcher was not created at $output"
        }
    }
}

abstract class CreateRuntimeImage : DefaultTask() {
    @get:InputDirectory
    abstract val javaHome: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun create() {
        execOperations.javaexec {
            executable(javaLauncher.get().executablePath.asFile)
            classpath(generatorClasspath)
            mainClass.set("dev.w0fv1.norm.packaging.RuntimeImageGenerator")
            args(javaHome.get().asFile.absolutePath, outputDirectory.get().asFile.absolutePath)
        }.assertNormalExitValue()
    }
}

abstract class PrepareReachabilityMetadata : DefaultTask() {
    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceArchive: RegularFileProperty

    @get:Input
    abstract val offline: Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun prepare() {
        execOperations.javaexec {
            classpath(generatorClasspath)
            mainClass.set("dev.w0fv1.norm.packaging.ReachabilityMetadataArchive")
            args(
                outputFile.get().asFile.absolutePath,
                sourceArchive.orNull?.asFile?.absolutePath.orEmpty(),
                offline.get().toString(),
            )
        }.assertNormalExitValue()
    }
}

abstract class GenerateRuntimePayload : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Classpath
    abstract val generatorClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val archiveFile: RegularFileProperty

    @get:OutputFile
    abstract val digestFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun create() {
        execOperations.javaexec {
            classpath(generatorClasspath)
            mainClass.set("dev.w0fv1.norm.packaging.RuntimePayloadArchive")
            args(
                sourceDirectory.get().asFile.absolutePath,
                archiveFile.get().asFile.absolutePath,
                digestFile.get().asFile.absolutePath,
            )
        }.assertNormalExitValue()
    }
}

val standardLibraryDirectory = rootProject.file("norm/stdlib")
val generatedBuildMetadata = layout.buildDirectory.dir("generated/sources/build-metadata")
val builtinAbiFile = layout.projectDirectory.file("stdlib-abi.json")
val generatedBuiltinAbi = layout.buildDirectory.dir("generated/sources/builtin-abi")
val prepareReachabilityMetadata = tasks.register<PrepareReachabilityMetadata>(
    "prepareReachabilityMetadata",
) {
    sourceArchive.set(layout.file(providers.gradleProperty("normReachabilityMetadata").map { rootProject.file(it) }))
    offline.set(gradle.startParameter.isOffline)
    outputFile.set(
        layout.buildDirectory.file(
            "generated/resources/reachability-metadata/graalvm-reachability-metadata.zip",
        ),
    )
}
val codegen = sourceSets.create("codegen") {
    java.setSrcDirs(listOf(rootProject.file("build-tools/src/main/java")))
}
prepareReachabilityMetadata.configure {
    generatorClasspath.from(codegen.runtimeClasspath)
}
tasks.named<JavaCompile>(codegen.compileJavaTaskName) {
    modularity.inferModulePath = false
}

val generateBuildMetadata = tasks.register<JavaExec>("generateBuildMetadata") {
    val normVersion = project.version.toString()
    val graalVmVersion = libs.versions.graalvm.get()
    inputs.property("normVersion", normVersion)
    inputs.property("graalVmVersion", graalVmVersion)
    outputs.dir(generatedBuildMetadata)
    classpath = codegen.runtimeClasspath
    mainClass.set("dev.w0fv1.norm.codegen.BuildMetadataGenerator")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    })
    args(normVersion, graalVmVersion, generatedBuildMetadata.get().asFile.absolutePath)
}

val generateBuiltinAbi = tasks.register<GenerateBuiltinAbi>("generateBuiltinAbi") {
    generatorClasspath.from(codegen.runtimeClasspath)
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    })
    schemaFile.set(builtinAbiFile)
    outputDirectory.set(generatedBuiltinAbi)
}

val toolchainArtifacts = configurations.runtimeClasspath.get().incoming.artifacts.resolvedArtifacts
val toolchainGraph = configurations.runtimeClasspath.get().incoming.resolutionResult.rootComponent.map { root ->
    val graph = linkedMapOf<String, List<String>>()
    val pending = ArrayDeque<org.gradle.api.artifacts.result.ResolvedComponentResult>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val component = pending.removeFirst()
        val identity = component.id
        val key = if (component == root) "" else {
            check(identity is org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                "Toolchain component has no module identity: $identity"
            }
            "${identity.group}:${identity.module}:${identity.version}"
        }
        if (graph.containsKey(key)) continue
        val edges = component.dependencies.filterNot { it.isConstraint }.map { dependency ->
            check(dependency is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                "Unresolved toolchain dependency: ${dependency.requested}"
            }
            val selected = dependency.selected
            val module = selected.id
            check(module is org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                "Toolchain dependency has no module identity: $module"
            }
            pending.add(selected)
            "${module.group}:${module.module}:${module.version}"
        }.distinct().sorted()
        graph[key] = edges
    }
    graph.toMap()
}
val generateToolchainArtifacts = tasks.register<GenerateToolchainArtifacts>("generateToolchainArtifacts") {
    artifacts.from(configurations.runtimeClasspath)
    generatorClasspath.from(codegen.runtimeClasspath)
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    })
    mergedModules.set(mapOf(resolverProviderModule to resolverProviderMergedModules))
    roots.set(toolchainGraph.map { it.getValue("") })
    dependencies.set(toolchainGraph.map { it.filterKeys(String::isNotEmpty) })
    purposeRoots.set(toolchainGraph.map { graph ->
        val execution = (nativeExecution.allDependencies + nativeExecutionRuntime.allDependencies)
            .map { "${it.group}:${it.name}" }.toSet()
        val hosted = nativeHosted.allDependencies.map { "${it.group}:${it.name}" }.toSet()
        val roots = graph.getValue("")
        mapOf(
            "execution" to roots.filter { it.substringBeforeLast(':') in execution },
            "hosted" to roots.filter { it.substringBeforeLast(':') in hosted },
            "tooling" to roots.filter {
                val module = it.substringBeforeLast(':')
                module !in execution && module !in hosted
            },
        )
    })
    identities.set(toolchainArtifacts.map { resolved ->
        val entries = resolved.map { artifact ->
            val identity = artifact.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
                ?: error("Toolchain artifact has no module identity: ${artifact.id}")
            artifact.file.name to "${identity.group}:${identity.module}:${identity.version}"
        }
        check(entries.map { it.first }.distinct().size == entries.size) {
            "Duplicate toolchain artifact filenames"
        }
        entries.toMap()
    })
    outputDirectory.set(layout.buildDirectory.dir("generated/resources/toolchain-artifacts"))
}

sourceSets {
    main {
        java.srcDir(generatedBuildMetadata)
        java.srcDir(generatedBuiltinAbi)
        resources.srcDir(standardLibraryDirectory)
        resources.exclude("std/tests/**")
        resources.srcDir(generateToolchainArtifacts.flatMap { it.outputDirectory })
        resources.srcDir(prepareReachabilityMetadata.map { it.outputFile.get().asFile.parentFile })
    }
    test {
        java.srcDir(rootProject.file("build-tools/src/test/java"))
        resources.srcDir(rootProject.file("build-tools/src/test/resources"))
        resources.srcDir(rootProject.file("norm/tests"))
    }
}

tasks.compileJava {
    dependsOn(generateBuildMetadata, generateBuiltinAbi)
}

tasks.test {
    dependsOn(tasks.jar)
    inputs.dir(rootProject.file("norm/libraries")).withPropertyName("normLibraries")
    providers.gradleProperty("normTestMavenRepository").orNull?.let { repository ->
        val fixture = rootProject.file(repository)
        inputs.dir(fixture).withPropertyName("mavenTestRepository")
        systemProperty("norm.test.mavenRepository", fixture.absolutePath)
    }
    systemProperty("norm.test.abi", builtinAbiFile.asFile.absolutePath)
    systemProperty("norm.test.stdlib", rootProject.file("norm/stdlib/std").absolutePath)
    systemProperty("norm.test.modulePath", files(tasks.jar, configurations.runtimeClasspath).asPath)
}

dependencies {
    add(codegen.implementationConfigurationName, libs.gson)
    testImplementation(codegen.output)
    nativeExecution(libs.jackson.core)
    nativeExecution(libs.jackson.dataformat.yaml)
    nativeExecution(libs.woodstox)
    nativeExecution(libs.truffle.api)
    nativeExecution(libs.polyglot)
    nativeHosted(libs.nativeimage)
    implementation(libs.lsp4j)
    implementation(libs.gson)
    implementation(libs.commonmark)
    implementation(libs.maven.resolver.supplier)
    nativeHosted(libs.asm)
    implementation(libs.commons.codec)
    implementation(libs.commons.compress)
    implementation(libs.jcl.over.slf4j)
    implementation(libs.junit.platform.launcher)
    implementation(libs.junit.platform.engine)
    implementation(libs.apiguardian.api)
    nativeExecution(libs.objenesis)
    nativeHosted(libs.kryo)
    implementation(libs.graalvm.reachability.metadata)
    implementation(libs.jspecify)
    runtimeOnly(libs.truffle.runtime)
    runtimeOnly(libs.junit.jupiter.engine)
    nativeExecutionRuntime(libs.slf4j.simple)
    annotationProcessor(libs.truffle.dsl.processor)
    testImplementation(libs.archunit)
    testImplementation(libs.java.websocket)
}

extraJavaModuleInfo {
    automaticModule(
        "org.graalvm.buildtools:graalvm-reachability-metadata",
        "graalvm.reachability.metadata",
    )
    automaticModule("org.graalvm.buildtools:utils", "org.graalvm.buildtools.utils")
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j", "org.eclipse.lsp4j")
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc", "org.eclipse.lsp4j.jsonrpc")
    automaticModule(
        "org.graalvm.truffle:truffle-dsl-processor",
        "org.graalvm.truffle.dsl.processor",
    )
    automaticModule(resolverProviderModule, "maven.resolver.provider") {
        resolverProviderMergedModules.forEach { mergeJar(it) }
    }
}

application {
    applicationName = "norm"
    mainModule = "dev.w0fv1.norm"
    mainClass = "dev.w0fv1.norm.cli.Main"
    applicationDefaultJvmArgs =
        listOf(
            "--add-modules=java.se",
            "--sun-misc-unsafe-memory-access=allow",
            "--enable-native-access=org.graalvm.truffle",
        )
}

val runtimeJava = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
}

if (System.getProperty("os.name").startsWith("Windows")) {
    val hostDirectory = layout.buildDirectory.dir("native-host")
    val publishNativeHost = tasks.register<Exec>("publishNativeHost") {
        inputs.files(fileTree("../launcher/Norm.NativeHost") { include("*.cs", "*.csproj") })
        inputs.files(fileTree("../launcher/Norm.Launcher") { include("*.cs") })
        outputs.file(hostDirectory.map { it.file("native-host.exe") })
        commandLine(
            "dotnet", "publish", file("../launcher/Norm.NativeHost/Norm.NativeHost.csproj"),
            "-c", "Release", "-r", "win-x64", "--self-contained", "true",
            "-o", hostDirectory.get().asFile.absolutePath,
        )
    }
    tasks.processResources {
        dependsOn(publishNativeHost)
        from(hostDirectory) { include("native-host.exe") }
    }
}
val runtimeImageDirectory = layout.buildDirectory.dir("runtime-image")
val createRuntimeImage = tasks.register<CreateRuntimeImage>("createRuntimeImage") {
    javaHome.set(runtimeJava.map { it.metadata.installationPath })
    generatorClasspath.from(codegen.runtimeClasspath)
    javaLauncher.set(runtimeJava)
    outputDirectory.set(runtimeImageDirectory)
}

val runtimeLaunchers = tasks.register<GenerateRuntimeLaunchers>("generateRuntimeLaunchers") {
    moduleName.set(application.mainModule)
    entrypointClass.set(application.mainClass)
    launcherJvmArguments.set(application.applicationDefaultJvmArgs)
    generatorClasspath.from(codegen.runtimeClasspath)
    javaLauncher.set(runtimeJava)
    outputDirectory.set(layout.buildDirectory.dir("generated/runtime-launchers"))
}

distributions.create("runtime") {
    distributionBaseName.set("norm")
    contents {
        duplicatesStrategy = DuplicatesStrategy.FAIL
        from(tasks.jar) {
            into("lib")
        }
        from(configurations.runtimeClasspath) {
            into("lib")
        }
        from(runtimeLaunchers) {
            into("bin")
        }
        from(createRuntimeImage) {
            into("runtime")
        }
    }
}

distributions.configureEach {
    contents {
        from(rootProject.layout.projectDirectory) {
            include("LICENSE", "LICENSING.md")
        }
    }
}

val installRuntimeDistribution = tasks.named<Sync>("installRuntimeDist")
installRuntimeDistribution.configure {
    into(layout.buildDirectory.dir("install/norm-runtime"))
}
val runtimePayloadFile = layout.buildDirectory.file("launcher/norm-runtime.zip")
val runtimePayloadDigestFile = layout.buildDirectory.file("launcher/norm-runtime.sha256")
val runtimePayloadArchive = tasks.register<GenerateRuntimePayload>("runtimePayload") {
    dependsOn(installRuntimeDistribution)
    sourceDirectory.set(layout.buildDirectory.dir("install/norm-runtime"))
    generatorClasspath.from(codegen.runtimeClasspath)
    archiveFile.set(runtimePayloadFile)
    digestFile.set(runtimePayloadDigestFile)
}

tasks.register<PublishNativeLauncher>("publishWindowsExecutable") {
    dependsOn(runtimePayloadArchive)
    projectFile.set(rootProject.layout.projectDirectory.file("cli/launcher/Norm.Launcher/Norm.Launcher.csproj"))
    sourceFiles.from(
        rootProject.fileTree("cli/launcher/Norm.Launcher") {
            include("**/*.cs", "**/*.csproj")
        },
    )
    applicationIcon.set(rootProject.layout.projectDirectory.file("docs/public/brand/norm.ico"))
    runtimePayload.set(runtimePayloadFile)
    runtimePayloadDigest.set(runtimePayloadDigestFile)
    normVersion.set(project.version.toString())
    outputExecutable.set(layout.buildDirectory.file("launcher/publish/norm.exe"))
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.layout.projectDirectory)
}

tasks.jar {
    from(rootProject.layout.projectDirectory) {
        include("LICENSE", "LICENSING.md")
        into("META-INF")
    }
    manifest {
        attributes(
            "Implementation-Title" to "Norm Compiler",
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

tasks.register<Sync>("installVsCodeTestServer") {
    into(layout.buildDirectory.dir("vscode-test-server"))
    with(distributions.named("main").get().contents)
}
