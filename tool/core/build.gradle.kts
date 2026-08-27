import java.security.MessageDigest

description = "Norm compiler and canonical core"

abstract class GenerateIrSchema : DefaultTask() {
    @get:InputFile
    abstract val schemaFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val schema = groovy.json.JsonSlurper().parse(schemaFile.get().asFile) as Map<*, *>
        val categories = listOf(
            "expressions" to "EXPRESSIONS",
            "statements" to "STATEMENTS",
            "patterns" to "PATTERNS",
            "iterations" to "ITERATIONS",
            "witnessTargets" to "WITNESS_TARGETS",
        )
        val source = buildString {
            appendLine("package dev.w0fv1.norm.ir;")
            appendLine()
            appendLine("import java.util.List;")
            appendLine()
            appendLine("public final class IrSchema {")
            appendLine("  private IrSchema() {}")
            categories.forEach { (sourceName, fieldName) ->
                @Suppress("UNCHECKED_CAST")
                val variants = schema[sourceName] as List<Map<String, String>>
                appendLine()
                appendLine("  public static final List<Variant> $fieldName =")
                appendLine("      List.of(")
                variants.forEachIndexed { index, variant ->
                    val suffix = if (index + 1 == variants.size) ");" else ","
                    appendLine(
                        "          new Variant(\"${variant.getValue("kind")}\", " +
                            "\"${variant.getValue("bound")}\", " +
                            "\"${variant.getValue("core")}\")$suffix",
                    )
                }
            }
            appendLine()
            appendLine("  public record Variant(String kind, String boundType, String coreType) {}")
            appendLine("}")
        }
        val output = outputDirectory.file("dev/w0fv1/norm/ir/IrSchema.java").get().asFile
        output.parentFile.mkdirs()
        output.writeText(source)
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
        val exception = schema["exception"] as Map<String, Any>
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
        val packageName = exception.getValue("packageName")
        val typeName = exception.getValue("typeName")
        packageDirectory.resolve("ExceptionAbi.java").writeText(
            buildString {
                appendLine("package dev.w0fv1.norm.abi;")
                appendLine()
                appendLine("public final class ExceptionAbi {")
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
        val fingerprintInput = buildString {
            append(schema["version"]).append('\n')
            intrinsics.forEach {
                append(it.getValue("name"))
                    .append(':')
                    .append(it.getValue("requiresResultRuntimeType"))
                    .append('\n')
            }
            runtimeShapes.forEach { append(it).append('\n') }
            listOf("packageName", "typeName", "messageFieldName", "messageFieldOrdinal")
                .forEach { append(exception.getValue(it)).append('\n') }
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
val irSchemaFile = layout.projectDirectory.file("src/main/ir/bound-core-schema.json")
val generatedIrSchema = layout.buildDirectory.dir("generated/sources/ir-schema")
val builtinAbiFile = layout.projectDirectory.file("src/main/abi/builtin-abi.json")
val generatedBuiltinAbi = layout.buildDirectory.dir("generated/sources/builtin-abi")
val generateBuildMetadata = tasks.register<Copy>("generateBuildMetadata") {
    inputs.property("normVersion", project.version)
    from(layout.projectDirectory.dir("src/main/templates"))
    into(generatedBuildMetadata)
    expand("normVersion" to project.version.toString())
}

val generateIrSchema = tasks.register<GenerateIrSchema>("generateIrSchema") {
    schemaFile.set(irSchemaFile)
    outputDirectory.set(generatedIrSchema)
}

val generateBuiltinAbi = tasks.register<GenerateBuiltinAbi>("generateBuiltinAbi") {
    schemaFile.set(builtinAbiFile)
    outputDirectory.set(generatedBuiltinAbi)
}

sourceSets {
    main {
        java.srcDir(generatedBuildMetadata)
        java.srcDir(generatedIrSchema)
        java.srcDir(generatedBuiltinAbi)
        resources.srcDir(standardLibraryDirectory)
    }
    test {
        resources.srcDir(rootProject.file("norm/tests"))
    }
}

tasks.compileJava {
    dependsOn(generateBuildMetadata, generateIrSchema, generateBuiltinAbi)
}

dependencies {
    testImplementation(libs.archunit)
}
