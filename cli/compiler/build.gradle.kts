import java.security.MessageDigest

plugins {
    application
    alias(libs.plugins.graalvm.native)
    id("org.gradlex.extra-java-module-info") version "1.14.2"
}

description = "Norm compiler"

abstract class GenerateBuildMetadata : DefaultTask() {
    @get:Input
    abstract val normVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.file("dev/w0fv1/norm/value/BuildMetadata.java").get().asFile
        output.parentFile.mkdirs()
        val versionLiteral = groovy.json.JsonOutput.toJson(normVersion.get())
        output.writeText(
            """
            package dev.w0fv1.norm.value;

            public final class BuildMetadata {
              public static final String VERSION = $versionLiteral;

              private BuildMetadata() {}
            }
            """.trimIndent() + "\n",
        )
    }
}

abstract class GenerateBuiltinAbi : DefaultTask() {
    @get:InputFile
    abstract val schemaFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val schemaBytes = schemaFile.get().asFile.readBytes()
        val schema = groovy.json.JsonSlurper().parse(schemaBytes) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val intrinsics = schema["intrinsics"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val runtimeShapes = schema["runtimeShapes"] as List<String>
        @Suppress("UNCHECKED_CAST")
        val opaqueValues = schema["opaqueValues"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val exception = schema["exception"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val serialization = schema["serialization"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val json = schema["json"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val xml = schema["xml"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val yaml = schema["yaml"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val filesystemPath = schema["filesystemPath"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val httpUri = schema["httpUri"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val timeDuration = schema["timeDuration"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val systemExceptions = schema["systemExceptions"] as Map<String, Map<String, Any>>
        fun constantName(name: String): String =
            name.replace(Regex("([a-z])([A-Z])"), "\$1_\$2").uppercase()
        val packageDirectory = outputDirectory.dir("dev/w0fv1/norm/abi").get().asFile
        packageDirectory.mkdirs()
        packageDirectory.resolve("IntrinsicId.java").writeText(
            buildString {
                appendLine("package dev.w0fv1.norm.abi;")
                appendLine()
                appendLine("public enum IntrinsicId {")
                intrinsics.forEachIndexed { index, intrinsic ->
                    val suffix = if (index + 1 == intrinsics.size) ";" else ","
                    appendLine(
                        "  ${intrinsic.getValue("name")}(" +
                            "${intrinsic.getValue("requiresResultRuntimeType")})$suffix",
                    )
                }
                appendLine()
                appendLine("  private final boolean requiresResultRuntimeType;")
                appendLine()
                appendLine("  IntrinsicId(boolean requiresResultRuntimeType) {")
                appendLine("    this.requiresResultRuntimeType = requiresResultRuntimeType;")
                appendLine("  }")
                appendLine()
                appendLine("  public boolean requiresResultRuntimeType() {")
                appendLine("    return requiresResultRuntimeType;")
                appendLine("  }")
                appendLine("}")
            },
        )
        packageDirectory.resolve("RuntimeShape.java").writeText(
            buildString {
                appendLine("package dev.w0fv1.norm.abi;")
                appendLine()
                appendLine("public enum RuntimeShape {")
                runtimeShapes.forEachIndexed { index, shape ->
                    val suffix = if (index + 1 == runtimeShapes.size) "" else ","
                    appendLine("  $shape$suffix")
                }
                appendLine("}")
            },
        )
        packageDirectory.resolve("OpaqueValueAbi.java").writeText(
            buildString {
                appendLine("package dev.w0fv1.norm.abi;")
                appendLine()
                appendLine("public final class OpaqueValueAbi {")
                opaqueValues.forEach { value ->
                    val name = value.getValue("name")
                    appendLine(
                        "  public static final Identity $name = new Identity(" +
                            "\"${value.getValue("moduleName")}\", " +
                            "${value.getValue("moduleVersion")}, " +
                            "\"${value.getValue("packageName")}\", " +
                            "\"${value.getValue("typeName")}\");",
                    )
                }
                appendLine()
                appendLine(
                    "  public record Identity(String moduleName, int moduleVersion, " +
                        "String packageName, String typeName) {}",
                )
                appendLine()
                appendLine("  private OpaqueValueAbi() {}")
                appendLine("}")
            },
        )
        val packageName = exception.getValue("packageName")
        val typeName = exception.getValue("typeName")
        packageDirectory.resolve("ExceptionAbi.java").writeText(
            buildString {
                appendLine("package dev.w0fv1.norm.abi;")
                appendLine()
                appendLine("public final class ExceptionAbi {")
                appendLine(
                    "  public static final String MODULE_NAME = \"${exception.getValue("moduleName")}\";",
                )
                appendLine(
                    "  public static final int MODULE_VERSION = ${exception.getValue("moduleVersion")};",
                )
                appendLine("  public static final String PACKAGE_NAME = \"$packageName\";")
                appendLine("  public static final String TYPE_NAME = \"$typeName\";")
                appendLine("  public static final String IDENTITY = PACKAGE_NAME + \".\" + TYPE_NAME;")
                appendLine(
                    "  public static final String MESSAGE_FIELD_NAME = " +
                        "\"${exception.getValue("messageFieldName")}\";",
                )
                appendLine(
                    "  public static final int MESSAGE_FIELD_ORDINAL = " +
                        "${exception.getValue("messageFieldOrdinal")};",
                )
                appendLine()
                appendLine("  private ExceptionAbi() {}")
                appendLine("}")
            },
        )
        fun generateValueAbi(className: String, contract: Map<String, Any>) {
            packageDirectory.resolve("$className.java").writeText(buildString {
                appendLine("package dev.w0fv1.norm.abi;")
                appendLine()
                appendLine("public final class $className {")
                contract.entries.forEach { (name, value) ->
                    when (value) {
                        is String ->
                            appendLine(
                                "  public static final String ${constantName(name)} = \"$value\";",
                            )
                        is Number ->
                            appendLine(
                                "  public static final int ${constantName(name)} = $value;",
                            )
                    }
                }
                appendLine()
                appendLine("  private $className() {}")
                appendLine("}")
            })
        }
        generateValueAbi("FilesystemPathAbi", filesystemPath)
        generateValueAbi("HttpUriAbi", httpUri)
        generateValueAbi("TimeDurationAbi", timeDuration)
        packageDirectory.resolve("SerializationAbi.java").writeText(
            buildString {
                appendLine("package dev.w0fv1.norm.abi;")
                appendLine()
                appendLine("public final class SerializationAbi {")
                serialization.entries.forEach { (name, value) ->
                    if (value is String) {
                        appendLine(
                            "  public static final String ${constantName(name)} = \"$value\";",
                        )
                    }
                }
                appendLine(
                    "  public static final int MODULE_VERSION = " +
                        "${serialization.getValue("moduleVersion")};",
                )
                appendLine()
                appendLine("  private SerializationAbi() {}")
                appendLine("}")
            },
        )
        fun generateFormatAbi(domain: String, contract: Map<String, Any>) {
            @Suppress("UNCHECKED_CAST")
            val fields = contract.getValue("fields") as List<Map<String, Any>>
            @Suppress("UNCHECKED_CAST")
            val intrinsicNames = contract.getValue("intrinsicNames") as List<String>
            @Suppress("UNCHECKED_CAST")
            val variants = contract["variants"] as? List<String> ?: listOf()
            val className = domain.replaceFirstChar(Char::uppercase) + "Abi"
            packageDirectory.resolve("$className.java").writeText(
                buildString {
                    appendLine("package dev.w0fv1.norm.abi;")
                    appendLine()
                    if (variants.isNotEmpty()) appendLine("import java.util.List;")
                    appendLine("import java.util.Set;")
                    appendLine()
                    appendLine("public final class $className {")
                    contract.entries
                        .filter { it.value is String }
                        .forEach { (name, value) ->
                            appendLine(
                                "  public static final String ${constantName(name)} = \"$value\";",
                            )
                        }
                    appendLine(
                        "  public static final int MODULE_VERSION = " +
                            "${contract.getValue("moduleVersion")};",
                    )
                    fields.forEach { field ->
                        val fieldName = constantName(field.getValue("name").toString())
                        appendLine(
                            "  public static final String FIELD_${fieldName}_NAME = " +
                                "\"${field.getValue("name")}\";",
                        )
                        appendLine(
                            "  public static final int FIELD_${fieldName}_ORDINAL = " +
                                "${field.getValue("ordinal")};",
                        )
                    }
                    variants.forEach { variant ->
                        appendLine(
                            "  public static final String VALUE_VARIANT_${constantName(variant)} = " +
                                "\"$variant\";",
                        )
                    }
                    appendLine()
                    appendLine("  public static final Set<String> INTRINSIC_NAMES =")
                    appendLine("      Set.of(")
                    intrinsicNames.forEachIndexed { index, intrinsic ->
                        val suffix = if (index + 1 == intrinsicNames.size) ");" else ","
                        appendLine("          \"$intrinsic\"$suffix")
                    }
                    if (variants.isNotEmpty()) {
                        appendLine("  public static final List<String> VALUE_VARIANTS =")
                        appendLine("      List.of(")
                        variants.forEachIndexed { index, variant ->
                            val suffix = if (index + 1 == variants.size) ");" else ","
                            appendLine("          VALUE_VARIANT_${constantName(variant)}$suffix")
                        }
                    }
                    appendLine()
                    appendLine("  private $className() {}")
                    appendLine("}")
                },
            )
        }
        generateFormatAbi("json", json)
        generateFormatAbi("xml", xml)
        generateFormatAbi("yaml", yaml)
        systemExceptions.forEach { (domain, contract) ->
            @Suppress("UNCHECKED_CAST")
            val fields = contract.getValue("fields") as List<Map<String, Any>>
            @Suppress("UNCHECKED_CAST")
            val intrinsicNames = contract.getValue("intrinsicNames") as List<String>
            @Suppress("UNCHECKED_CAST")
            val operations = contract.getValue("operations") as List<Map<String, String>>
            @Suppress("UNCHECKED_CAST")
            val failures = contract.getValue("failures") as List<Map<String, String>>
            val className = domain.replaceFirstChar(Char::uppercase) + "ExceptionAbi"
            packageDirectory.resolve("$className.java").writeText(
                buildString {
                    appendLine("package dev.w0fv1.norm.abi;")
                    appendLine()
                    appendLine("import java.util.Map;")
                    appendLine("import java.util.Set;")
                    appendLine()
                    appendLine("public final class $className {")
                    contract.entries
                        .filter { it.value is String }
                        .forEach { (name, value) ->
                            appendLine(
                                "  public static final String ${constantName(name)} = \"$value\";",
                            )
                        }
                    appendLine(
                        "  public static final int MODULE_VERSION = " +
                            "${contract.getValue("moduleVersion")};",
                    )
                    fields.forEach { field ->
                        val fieldName = constantName(field.getValue("name").toString())
                        appendLine(
                            "  public static final String FIELD_${fieldName}_NAME = " +
                                "\"${field.getValue("name")}\";",
                        )
                        appendLine(
                            "  public static final int FIELD_${fieldName}_ORDINAL = " +
                                "${field.getValue("ordinal")};",
                        )
                    }
                    appendLine()
                    appendLine("  public static final Set<String> INTRINSIC_NAMES =")
                    appendLine("      Set.of(")
                    intrinsicNames.forEachIndexed { index, intrinsic ->
                        val suffix = if (index + 1 == intrinsicNames.size) ");" else ","
                        appendLine("          \"$intrinsic\"$suffix")
                    }
                    appendLine()
                    appendLine("  private static final Map<String, String> OPERATIONS =")
                    appendLine("      Map.ofEntries(")
                    operations.forEachIndexed { index, operation ->
                        val suffix = if (index + 1 == operations.size) ");" else ","
                        appendLine(
                            "          Map.entry(\"${operation.getValue("platformName")}\", " +
                                "\"${operation.getValue("variant")}\")$suffix",
                        )
                    }
                    appendLine("  private static final Map<String, Failure> FAILURES =")
                    appendLine("      Map.ofEntries(")
                    failures.forEachIndexed { index, failure ->
                        val suffix = if (index + 1 == failures.size) ");" else ","
                        appendLine(
                            "          Map.entry(\"${failure.getValue("platformName")}\", " +
                                "new Failure(\"${failure.getValue("variant")}\", " +
                                "\"${failure.getValue("code")}\"))$suffix",
                        )
                    }
                    appendLine()
                    appendLine("  public static String operationVariant(String platformName) {")
                    appendLine("    String variant = OPERATIONS.get(platformName);")
                    appendLine(
                        "    if (variant == null) throw new IllegalArgumentException(" +
                            "\"unknown $domain operation \" + platformName);",
                    )
                    appendLine("    return variant;")
                    appendLine("  }")
                    appendLine()
                    appendLine("  public static Failure failure(String platformName) {")
                    appendLine("    Failure failure = FAILURES.get(platformName);")
                    appendLine(
                        "    if (failure == null) throw new IllegalArgumentException(" +
                            "\"unknown $domain failure \" + platformName);",
                    )
                    appendLine("    return failure;")
                    appendLine("  }")
                    appendLine()
                    appendLine("  public record Failure(String variant, String code) {}")
                    appendLine()
                    appendLine("  private $className() {}")
                    appendLine("}")
                },
            )
        }
        val fingerprintInput = buildString {
            append(schema["version"]).append('\n')
            intrinsics.forEach {
                append(it.getValue("name"))
                    .append(':')
                    .append(it.getValue("requiresResultRuntimeType"))
                    .append('\n')
            }
            runtimeShapes.forEach { append(it).append('\n') }
            append(groovy.json.JsonOutput.toJson(opaqueValues)).append('\n')
            listOf("packageName", "typeName", "messageFieldName", "messageFieldOrdinal")
                .forEach { append(exception.getValue(it)).append('\n') }
            append(groovy.json.JsonOutput.toJson(systemExceptions)).append('\n')
            append(groovy.json.JsonOutput.toJson(serialization)).append('\n')
            append(groovy.json.JsonOutput.toJson(json)).append('\n')
            append(groovy.json.JsonOutput.toJson(xml)).append('\n')
        }.toByteArray(Charsets.UTF_8)
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(fingerprintInput)
            .joinToString("") { "%02x".format(it) }
        packageDirectory.resolve("BuiltinAbi.java").writeText(
            buildString {
                appendLine("package dev.w0fv1.norm.abi;")
                appendLine()
                appendLine("public final class BuiltinAbi {")
                appendLine("  public static final int VERSION = ${schema["version"]};")
                appendLine("  public static final String FINGERPRINT = \"$fingerprint\";")
                appendLine()
                appendLine("  private BuiltinAbi() {}")
                appendLine("}")
            },
        )
    }
}

val standardLibraryDirectory = rootProject.file("norm/stdlib")
val generatedBuildMetadata = layout.buildDirectory.dir("generated/sources/build-metadata")
val builtinAbiFile = layout.projectDirectory.file("stdlib-abi.json")
val generatedBuiltinAbi = layout.buildDirectory.dir("generated/sources/builtin-abi")
val generateBuildMetadata = tasks.register<GenerateBuildMetadata>("generateBuildMetadata") {
    normVersion.set(project.version.toString())
    outputDirectory.set(generatedBuildMetadata)
}

val generateBuiltinAbi = tasks.register<GenerateBuiltinAbi>("generateBuiltinAbi") {
    schemaFile.set(builtinAbiFile)
    outputDirectory.set(generatedBuiltinAbi)
}

sourceSets {
    main {
        java.srcDir(generatedBuildMetadata)
        java.srcDir(generatedBuiltinAbi)
        resources.srcDir(standardLibraryDirectory)
    }
    test {
        resources.srcDir(rootProject.file("norm/tests"))
    }
}

tasks.compileJava {
    dependsOn(generateBuildMetadata, generateBuiltinAbi)
}

dependencies {
    implementation(libs.jackson.core)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.woodstox)
    implementation(libs.truffle.api)
    implementation(libs.polyglot)
    implementation(libs.lsp4j)
    implementation(libs.gson)
    implementation(libs.maven.resolver.supplier)
    implementation(libs.asm)
    implementation(libs.commons.codec)
    implementation(libs.jcl.over.slf4j)
    runtimeOnly(libs.truffle.runtime)
    runtimeOnly(libs.slf4j.nop)
    annotationProcessor(libs.truffle.dsl.processor)
    testImplementation(libs.archunit)
}

extraJavaModuleInfo {
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j", "org.eclipse.lsp4j")
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc", "org.eclipse.lsp4j.jsonrpc")
    automaticModule(
        "org.graalvm.truffle:truffle-dsl-processor",
        "org.graalvm.truffle.dsl.processor",
    )
    automaticModule("org.apache.maven:maven-resolver-provider", "org.apache.maven.resolver.provider") {
        mergeJar("org.apache.maven:maven-model-builder")
        mergeJar("org.apache.maven:maven-model")
        mergeJar("org.apache.maven:maven-repository-metadata")
        mergeJar("org.apache.maven:maven-artifact")
        mergeJar("org.apache.maven:maven-builder-support")
    }
}

application {
    applicationName = "norm"
    mainModule = "dev.w0fv1.norm"
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

val windowsHost = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val windowsResourceSource = layout.projectDirectory.file("scripts/windows-build/norm.rc")
val windowsResourceCompiler = layout.projectDirectory.file("scripts/windows-build/compile.ps1")
val windowsIcon = rootProject.layout.projectDirectory.file("docs/public/brand/norm.ico")
val windowsResourceDirectory = layout.buildDirectory.dir("generated/windows-resources")
val windowsResource = windowsResourceDirectory.map { it.file("norm.res") }
val compileWindowsResources = tasks.register<Exec>("compileWindowsResources") {
    inputs.files(windowsResourceCompiler, windowsResourceSource, windowsIcon)
    outputs.file(windowsResource)
    workingDir(rootProject.layout.projectDirectory)
    enabled = windowsHost
    commandLine(
        "powershell.exe",
        "-NoLogo",
        "-NoProfile",
        "-NonInteractive",
        "-File",
        windowsResourceCompiler.asFile.absolutePath,
        "-Source",
        windowsResourceSource.asFile.absolutePath,
        "-Output",
        windowsResource.get().asFile.absolutePath,
    )
}

if (windowsHost) {
    graalvmNative.binaries.named("main") {
        buildArgs.add("-H:NativeLinkerOption=${windowsResource.get().asFile.absolutePath}")
    }
    tasks.named("nativeCompile") {
        dependsOn(compileWindowsResources)
    }
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
