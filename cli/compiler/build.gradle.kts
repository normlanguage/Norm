import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
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

abstract class GenerateBuildMetadata : DefaultTask() {
    @get:Input
    abstract val normVersion: Property<String>

    @get:Input
    abstract val graalVmVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.file("dev/w0fv1/norm/value/BuildMetadata.java").get().asFile
        output.parentFile.mkdirs()
        val versionLiteral = groovy.json.JsonOutput.toJson(normVersion.get())
        val graalVmVersionLiteral = groovy.json.JsonOutput.toJson(graalVmVersion.get())
        output.writeText(
            """
            package dev.w0fv1.norm.value;

            public final class BuildMetadata {
              public static final String VERSION = $versionLiteral;
              public static final String GRAALVM_VERSION = $graalVmVersionLiteral;

              private BuildMetadata() {}
            }
            """.trimIndent() + "\n",
        )
    }
}

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

    @TaskAction
    fun generate() {
        val files = artifacts.files.associateBy { it.name }
        check(files.size == artifacts.files.size) { "Duplicate toolchain artifact filenames" }
        check(files.keys == identities.get().keys) { "Toolchain artifact identities do not match files" }
        val entries = identities.get().toSortedMap().map { (name, identity) ->
            val coordinate = identity.split(':')
            check(coordinate.size == 3) { "Invalid toolchain coordinate: $identity" }
            val components = listOf(identity) + mergedModules.get()[identity.substringBeforeLast(':')]
                .orEmpty().map { module ->
                    val matches = dependencies.get().keys.filter { it.substringBeforeLast(':') == module }
                    check(matches.size == 1) { "Merged toolchain component is not uniquely resolved: $module" }
                    matches.single()
                }
            val digest = MessageDigest.getInstance("SHA-256")
            files.getValue(name).inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            mapOf(
                "file" to name,
                "group" to coordinate[0],
                "artifact" to coordinate[1],
                "version" to coordinate[2],
                "components" to components.sorted(),
                "sha256" to digest.digest().joinToString("") { "%02x".format(it) },
            )
        }
        val output = outputDirectory.file("toolchain-artifacts.json").get().asFile
        output.parentFile.mkdirs()
        output.writeText(groovy.json.JsonOutput.toJson(
            mapOf("schemaVersion" to 1, "artifacts" to entries,
                "roots" to roots.get(), "dependencies" to dependencies.get().toSortedMap(),
                "purposes" to purposeRoots.get().toSortedMap()),
        ) + "\n")
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
    abstract val mainClass: Property<String>

    @get:Input
    abstract val jvmArguments: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        val invocation =
            jvmArguments.get().joinToString(" ") +
                " --module-path \"\$APP_HOME/lib\" --module " +
                "${moduleName.get()}/${mainClass.get()} \"\$@\""
        val unix = directory.resolve("norm")
        unix.writeText(
            """
            #!/bin/sh
            APP_HOME=${'$'}(CDPATH= cd -- "${'$'}(dirname -- "${'$'}0")/.." && pwd)
            exec "${'$'}APP_HOME/runtime/bin/java" $invocation
            """.trimIndent() + "\n",
        )
        unix.setExecutable(true, false)
        val windowsArguments = jvmArguments.get().joinToString(" ")
        directory.resolve("norm.bat").writeText(
            """
            @echo off
            setlocal
            set "APP_HOME=%~dp0.."
            "%APP_HOME%\runtime\bin\java.exe" $windowsArguments --module-path "%APP_HOME%\lib" --module ${moduleName.get()}/${mainClass.get()} %*
            """.trimIndent().replace("\n", "\r\n") + "\r\n",
        )
        directory.resolve("launcher.json").writeText(
            groovy.json.JsonOutput.prettyPrint(
                groovy.json.JsonOutput.toJson(
                    mapOf(
                        "module" to "${moduleName.get()}/${mainClass.get()}",
                        "jvmArguments" to jvmArguments.get(),
                    ),
                ),
            ) + "\n",
        )
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
        val digest = MessageDigest.getInstance("SHA-256")
        runtimePayload.get().asFile.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val digestFile = temporaryDir.resolve("norm-runtime.sha256")
        digestFile.writeText(digest.digest().joinToString("") { "%02x".format(it) })
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
                "-p:NormPayloadDigestPath=${digestFile.absolutePath}",
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

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun create() {
        val output = outputDirectory.get().asFile
        check(!output.exists() || output.deleteRecursively()) {
            "Cannot replace runtime image $output"
        }
        val bin = javaHome.get().dir("bin").asFile
        val jlink = listOf("jlink", "jlink.exe").map(bin::resolve).firstOrNull(File::isFile)
            ?: error("jlink is unavailable in ${javaHome.get().asFile}")
        val java = listOf("java", "java.exe").map(bin::resolve).firstOrNull(File::isFile)
            ?: error("java is unavailable in ${javaHome.get().asFile}")
        val moduleOutput = ByteArrayOutputStream()
        execOperations.exec {
            executable(java)
            args("--list-modules")
            standardOutput = moduleOutput
        }
        val modules = moduleOutput.toString(Charsets.UTF_8)
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.substringBefore('@') }
            .sorted()
            .joinToString(",")
        check(modules.isNotEmpty()) {
            "No system modules are available in ${javaHome.get().asFile}"
        }
        execOperations.exec {
            executable(jlink)
            args(
                "--add-modules",
                modules,
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress",
                "zip-6",
                "--output",
                output.absolutePath,
            )
        }
    }
}

abstract class FetchReachabilityMetadata : DefaultTask() {
    @get:Input
    abstract val metadataVersion: Property<String>

    @get:Input
    abstract val expectedSha256: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun fetch() {
        val version = metadataVersion.get()
        val source = URI(
            "https://github.com/oracle/graalvm-reachability-metadata/releases/download/" +
                "$version/graalvm-reachability-metadata-$version.zip",
        )
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        val temporary = temporaryDir.resolve("reachability-metadata.zip")
        val clientBuilder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
        environmentProxy(source)?.let { clientBuilder.proxy(ProxySelector.of(it)) }
        clientBuilder.build().use { client ->
            val request = HttpRequest.newBuilder(source).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary.toPath()))
            check(response.statusCode() in 200..299) {
                "Cannot download GraalVM reachability metadata: HTTP ${response.statusCode()}"
            }
        }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(temporary.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(actual.equals(expectedSha256.get(), ignoreCase = true)) {
            "GraalVM reachability metadata checksum mismatch: expected " +
                "${expectedSha256.get()}, got $actual"
        }
        temporary.copyTo(output, overwrite = true)
    }

    private fun environmentProxy(source: URI): InetSocketAddress? {
        val names = if (source.scheme.equals("https", ignoreCase = true)) {
            listOf("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy")
        } else {
            listOf("HTTP_PROXY", "http_proxy")
        }
        val proxy = names.firstNotNullOfOrNull { System.getenv(it)?.takeIf(String::isNotBlank) }
            ?: return null
        val uri = URI(proxy)
        val port = if (uri.port >= 0) uri.port else 80
        return InetSocketAddress.createUnresolved(uri.host, port)
    }
}

val standardLibraryDirectory = rootProject.file("norm/stdlib")
val generatedBuildMetadata = layout.buildDirectory.dir("generated/sources/build-metadata")
val builtinAbiFile = layout.projectDirectory.file("stdlib-abi.json")
val generatedBuiltinAbi = layout.buildDirectory.dir("generated/sources/builtin-abi")
val reachabilityMetadataVersion = "1.0.13"
val fetchReachabilityMetadata = tasks.register<FetchReachabilityMetadata>(
    "fetchReachabilityMetadata",
) {
    metadataVersion.set(reachabilityMetadataVersion)
    expectedSha256.set("b94893e10448a37a604d24758418dc006223a64827146f0886af172bbbdd818a")
    outputFile.set(
        layout.buildDirectory.file(
            "generated/resources/reachability-metadata/graalvm-reachability-metadata.zip",
        ),
    )
}
val generateBuildMetadata = tasks.register<GenerateBuildMetadata>("generateBuildMetadata") {
    normVersion.set(project.version.toString())
    graalVmVersion.set(libs.versions.graalvm)
    outputDirectory.set(generatedBuildMetadata)
}

val codegen = sourceSets.create("codegen")
tasks.named<JavaCompile>(codegen.compileJavaTaskName) {
    modularity.inferModulePath = false
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
        resources.srcDir(fetchReachabilityMetadata.map { it.outputFile.get().asFile.parentFile })
    }
    test {
        resources.srcDir(rootProject.file("norm/tests"))
    }
}

tasks.compileJava {
    dependsOn(generateBuildMetadata, generateBuiltinAbi)
}

tasks.test {
    dependsOn(tasks.jar)
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
    nativeExecution(libs.objenesis)
    nativeHosted(libs.kryo)
    implementation(libs.graalvm.reachability.metadata)
    implementation(libs.jspecify)
    runtimeOnly(libs.truffle.runtime)
    runtimeOnly(libs.junit.jupiter.engine)
    nativeExecutionRuntime(libs.slf4j.simple)
    annotationProcessor(libs.truffle.dsl.processor)
    testImplementation(libs.archunit)
}

extraJavaModuleInfo {
    automaticModule(
        "org.graalvm.buildtools:graalvm-reachability-metadata",
        "org.graalvm.reachability",
    )
    automaticModule("org.graalvm.buildtools:utils", "org.graalvm.buildtools.utils")
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j", "org.eclipse.lsp4j")
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc", "org.eclipse.lsp4j.jsonrpc")
    automaticModule(
        "org.graalvm.truffle:truffle-dsl-processor",
        "org.graalvm.truffle.dsl.processor",
    )
    automaticModule(resolverProviderModule, "org.apache.maven.resolver.provider") {
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
    outputDirectory.set(runtimeImageDirectory)
}

val runtimeLaunchers = tasks.register<GenerateRuntimeLaunchers>("generateRuntimeLaunchers") {
    moduleName.set(application.mainModule)
    mainClass.set(application.mainClass)
    jvmArguments.set(application.applicationDefaultJvmArgs)
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

val installRuntimeDistribution = tasks.named<Sync>("installRuntimeDist")
installRuntimeDistribution.configure {
    into(layout.buildDirectory.dir("install/norm-runtime"))
}
val runtimePayloadArchive = tasks.register<Zip>("runtimePayload") {
    dependsOn(installRuntimeDistribution)
    from(installRuntimeDistribution.map { it.destinationDir })
    archiveFileName.set("norm-runtime.zip")
    destinationDirectory.set(layout.buildDirectory.dir("launcher"))
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
    runtimePayload.set(runtimePayloadArchive.flatMap { it.archiveFile })
    normVersion.set(project.version.toString())
    outputExecutable.set(layout.buildDirectory.file("launcher/publish/norm.exe"))
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.layout.projectDirectory)
}

tasks.jar {
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
