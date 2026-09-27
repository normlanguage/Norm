package dev.w0fv1.norm.codegen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class BuiltinAbiGenerator {
  private static final Gson JSON =
      new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
  private final JsonObject schema;
  private final JsonObject patterns;
  private final List<JsonElement> intrinsics;
  private final List<JsonElement> shapes;
  private final Map<String, String> outputs = new LinkedHashMap<>();

  private BuiltinAbiGenerator(JsonObject schema) {
    this.schema = schema;
    patterns = schema.getAsJsonObject("typePatterns");
    intrinsics = schema.getAsJsonArray("intrinsics").asList();
    shapes = schema.getAsJsonArray("runtimeShapes").asList();
  }

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 2)
      throw new IllegalArgumentException("Expected ABI schema and output directory");
    generate(Path.of(arguments[0]), Path.of(arguments[1]));
  }

  public static void generate(Path input, Path output) throws IOException {
    var generator =
        new BuiltinAbiGenerator(
            JsonParser.parseString(Files.readString(input, StandardCharsets.UTF_8))
                .getAsJsonObject());
    generator.generate();
    Path directory = output.resolve("dev/w0fv1/norm/abi");
    Files.createDirectories(directory);
    for (var entry : generator.outputs.entrySet())
      Files.writeString(
          directory.resolve(entry.getKey() + ".java"), entry.getValue(), StandardCharsets.UTF_8);
    try (var files = Files.list(directory)) {
      for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
        String name = file.getFileName().toString();
        if (!generator.outputs.containsKey(name.substring(0, name.length() - 5)))
          Files.delete(file);
      }
    }
  }

  private void generate() {
    declarations();
    var text = new StringBuilder("package dev.w0fv1.norm.abi;\n\npublic enum IntrinsicId {\n");
    for (int i = 0; i < intrinsics.size(); i++) {
      var entry = intrinsics.get(i).getAsJsonObject();
      text.append("  ")
          .append(entry.get("name").getAsString())
          .append('(')
          .append(entry.get("requiresResultRuntimeType"))
          .append(i + 1 == intrinsics.size() ? ");\n" : "),\n");
    }
    text.append(
        "\n  private final boolean requiresResultRuntimeType;\n\n  IntrinsicId(boolean requiresResultRuntimeType) {\n    this.requiresResultRuntimeType = requiresResultRuntimeType;\n  }\n\n  public boolean requiresResultRuntimeType() {\n    return requiresResultRuntimeType;\n  }\n}\n");
    outputs.put("IntrinsicId", text.toString());
    text = new StringBuilder("package dev.w0fv1.norm.abi;\n\npublic enum RuntimeShape {\n");
    for (int i = 0; i < shapes.size(); i++)
      text.append("  ")
          .append(shapes.get(i).getAsString())
          .append(i + 1 == shapes.size() ? "\n" : ",\n");
    outputs.put("RuntimeShape", text.append("}\n").toString());
    text = header("OpaqueValueAbi", "");
    for (var element : schema.getAsJsonArray("opaqueValues")) {
      var value = element.getAsJsonObject();
      text.append("  public static final Identity ")
          .append(value.get("name").getAsString())
          .append(" = new Identity(\"")
          .append(value.get("moduleName").getAsString())
          .append("\", ")
          .append(value.get("moduleVersion"))
          .append(", \"")
          .append(value.get("packageName").getAsString())
          .append("\", \"")
          .append(value.get("typeName").getAsString())
          .append("\");\n");
    }
    text.append(
        "\n  public record Identity(String moduleName, int moduleVersion, String packageName, String typeName) {}\n");
    finish("OpaqueValueAbi", text);
    var exception = schema.getAsJsonObject("exception");
    text = header("ExceptionAbi", "");
    text.append("  public static final String MODULE_NAME = \"")
        .append(exception.get("moduleName").getAsString())
        .append("\";\n");
    text.append("  public static final int MODULE_VERSION = ")
        .append(exception.get("moduleVersion"))
        .append(";\n");
    text.append("  public static final String PACKAGE_NAME = \"")
        .append(exception.get("packageName").getAsString())
        .append("\";\n");
    text.append("  public static final String TYPE_NAME = \"")
        .append(exception.get("typeName").getAsString())
        .append("\";\n");
    text.append("  public static final String IDENTITY = PACKAGE_NAME + \".\" + TYPE_NAME;\n");
    text.append("  public static final String MESSAGE_FIELD_NAME = \"")
        .append(exception.get("messageFieldName").getAsString())
        .append("\";\n");
    text.append("  public static final int MESSAGE_FIELD_ORDINAL = ")
        .append(exception.get("messageFieldOrdinal"))
        .append(";\n");
    finish("ExceptionAbi", text);
    valueAbi("FilesystemPathAbi", schema.getAsJsonObject("filesystemPath"));
    valueAbi("HttpUriAbi", schema.getAsJsonObject("httpUri"));
    valueAbi("TimeDurationAbi", schema.getAsJsonObject("timeDuration"));
    valueAbi("ConfigurationAbi", schema.getAsJsonObject("configuration"));
    text = header("SerializationAbi", "");
    var serialization = schema.getAsJsonObject("serialization");
    stringConstants(text, serialization);
    text.append("  public static final int MODULE_VERSION = ")
        .append(serialization.get("moduleVersion"))
        .append(";\n");
    finish("SerializationAbi", text);
    for (String domain : List.of("json", "xml", "yaml"))
      formatAbi(domain, schema.getAsJsonObject(domain), false);
    for (var entry : schema.getAsJsonObject("systemExceptions").entrySet())
      formatAbi(entry.getKey(), entry.getValue().getAsJsonObject(), true);
    var fingerprintInput = new StringBuilder().append(schema.get("version")).append('\n');
    for (var element : intrinsics) {
      var entry = element.getAsJsonObject();
      fingerprintInput
          .append(entry.get("name").getAsString())
          .append(':')
          .append(entry.get("requiresResultRuntimeType"))
          .append('\n');
    }
    shapes.forEach(shape -> fingerprintInput.append(shape.getAsString()).append('\n'));
    fingerprintInput.append(literal(schema.get("opaqueValues"))).append('\n');
    for (String key : List.of("packageName", "typeName", "messageFieldName", "messageFieldOrdinal"))
      fingerprintInput.append(exception.get(key).getAsString()).append('\n');
    for (String key : List.of("systemExceptions", "serialization", "json", "xml"))
      fingerprintInput.append(literal(schema.get(key))).append('\n');
    String fingerprint;
    try {
      fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(fingerprintInput.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
    outputs.put(
        "BuiltinAbi",
        "package dev.w0fv1.norm.abi;\n\npublic final class BuiltinAbi {\n  public static final int VERSION = "
            + schema.get("version")
            + ";\n  public static final String FINGERPRINT = \""
            + fingerprint
            + "\";\n\n  private BuiltinAbi() {}\n}\n");
  }

  private void declarations() {
    var text =
        new StringBuilder(
            "package dev.w0fv1.norm.abi;\nfinal class BuiltinDeclarations {\n  private static final java.util.Map<String, AbiType> TYPES = patterns();\n  private BuiltinDeclarations() {}\n  static AbiType type(String identity) { return java.util.Objects.requireNonNull(TYPES.get(identity), identity); }\n  private static java.util.Map<String, AbiType> patterns() {\n    var types = new java.util.LinkedHashMap<String, AbiType>();\n");
    Set<String> emitted = new HashSet<>();
    Set<String> pending = new HashSet<>();
    for (String key : patterns.keySet()) emitPattern(key, text, emitted, pending);
    text.append("    return java.util.Map.copyOf(types);\n  }\n");
    var types = schema.getAsJsonArray("builtinTypes");
    text.append(
            "  static java.util.List<BuiltinContracts.TypeDefinition> types() { return java.util.List.of(")
        .append(
            IntStream.range(0, types.size())
                .mapToObj(i -> "type" + i + "()")
                .collect(Collectors.joining(", ")))
        .append("); }\n");
    for (int i = 0; i < types.size(); i++) {
      var entry = types.get(i).getAsJsonObject();
      if (!shapes.contains(entry.get("runtimeShape")))
        throw new IllegalArgumentException("Unknown ABI runtime shape");
      text.append("  private static BuiltinContracts.TypeDefinition type")
          .append(i)
          .append("() { return new BuiltinContracts.TypeDefinition(")
          .append(symbol(entry.get("symbol")))
          .append(", RuntimeShape.")
          .append(entry.get("runtimeShape").getAsString())
          .append(", ")
          .append(optional(entry.get("constructor"), this::constructor))
          .append(", ")
          .append(optional(entry.get("collectionLiteral"), this::intrinsic))
          .append(", ")
          .append(entry.get("defaultCollectionLiteral"))
          .append(", ")
          .append(optional(entry.get("iterable"), this::iterable))
          .append(", ")
          .append(optional(entry.get("index"), this::index))
          .append(", ")
          .append(list(entry.get("members"), this::member))
          .append(", ")
          .append(list(entry.get("typeMembers"), this::member))
          .append(", ")
          .append(entry.get("hidden"))
          .append("); }\n");
    }
    var globals = schema.getAsJsonArray("builtinGlobals");
    text.append(
            "  static java.util.List<BuiltinContracts.GlobalDefinition> globals() { return java.util.List.of(")
        .append(
            IntStream.range(0, globals.size())
                .mapToObj(i -> "global" + i + "()")
                .collect(Collectors.joining(", ")))
        .append("); }\n");
    for (int i = 0; i < globals.size(); i++) {
      var entry = globals.get(i).getAsJsonObject();
      text.append("  private static BuiltinContracts.GlobalDefinition global")
          .append(i)
          .append("() { return new BuiltinContracts.GlobalDefinition(")
          .append(symbol(entry.get("symbol")))
          .append(", ")
          .append(intrinsic(entry.get("intrinsic")))
          .append("); }\n");
    }
    outputs.put("BuiltinDeclarations", text.append("}\n").toString());
  }

  private void emitPattern(
      String key, StringBuilder text, Set<String> emitted, Set<String> pending) {
    if (emitted.contains(key)) return;
    if (!pending.add(key)) throw new IllegalArgumentException("Cyclic ABI type pattern: " + key);
    if (!patterns.has(key)) throw new IllegalArgumentException("Unknown ABI type pattern: " + key);
    var entry = patterns.getAsJsonObject(key);
    for (var argument : entry.getAsJsonArray("arguments"))
      emitPattern(argument.getAsString(), text, emitted, pending);
    text.append("    types.put(")
        .append(literal(key))
        .append(", new AbiType(AbiType.Kind.")
        .append(entry.get("kind").getAsString())
        .append(", ")
        .append(literal(entry.get("identity")))
        .append(", ")
        .append(literal(entry.get("name")))
        .append(", ")
        .append(list(entry.get("arguments"), value -> "types.get(" + literal(value) + ")"))
        .append(", AbiType.Category.")
        .append(entry.get("category").getAsString())
        .append(", ")
        .append(entry.get("nullable"))
        .append("));\n");
    pending.remove(key);
    emitted.add(key);
  }

  private String type(JsonElement value) {
    if (value == null
        || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isString()
        || !patterns.has(value.getAsString()))
      throw new IllegalArgumentException("Unknown ABI type pattern: " + value);
    return "type(" + literal(value) + ")";
  }

  private String intrinsic(JsonElement value) {
    if (intrinsics.stream().noneMatch(entry -> entry.getAsJsonObject().get("name").equals(value)))
      throw new IllegalArgumentException("Unknown ABI intrinsic: " + value);
    return "IntrinsicId." + value.getAsString();
  }

  private String parameter(JsonElement value) {
    var entry = value.getAsJsonObject();
    return "new BuiltinContracts.Parameter("
        + literal(entry.get("name"))
        + ", "
        + type(entry.get("type"))
        + ", "
        + entry.get("hasDefault")
        + ")";
  }

  private String typeParameter(JsonElement value) {
    var entry = value.getAsJsonObject();
    return "new BuiltinContracts.TypeParameter("
        + literal(entry.get("name"))
        + ", "
        + type(entry.get("type"))
        + ", "
        + optional(entry.get("upperBound"), this::type)
        + ", "
        + optional(entry.get("defaultType"), this::type)
        + ")";
  }

  private String symbol(JsonElement value) {
    var entry = value.getAsJsonObject();
    return "new BuiltinContracts.Symbol("
        + literal(entry.get("name"))
        + ", BuiltinContracts.SymbolKind."
        + entry.get("kind").getAsString()
        + ", "
        + type(entry.get("type"))
        + ", "
        + list(entry.get("typeParameters"), this::typeParameter)
        + ", "
        + list(entry.get("parameters"), this::parameter)
        + ", "
        + literal(entry.get("documentation"))
        + ")";
  }

  private String member(JsonElement value) {
    var entry = value.getAsJsonObject();
    return "new BuiltinContracts.MemberDefinition("
        + symbol(entry.get("symbol"))
        + ", "
        + intrinsic(entry.get("intrinsic"))
        + ", "
        + optional(entry.get("writeIntrinsic"), this::intrinsic)
        + ")";
  }

  private String constructor(JsonElement value) {
    var entry = value.getAsJsonObject();
    return "new BuiltinContracts.ConstructorCapability("
        + list(entry.get("parameters"), this::parameter)
        + ", "
        + intrinsic(entry.get("intrinsic"))
        + ")";
  }

  private String iterable(JsonElement value) {
    var entry = value.getAsJsonObject();
    return "new BuiltinContracts.IterableCapability("
        + type(entry.get("elementType"))
        + ", "
        + intrinsic(entry.get("intrinsic"))
        + ")";
  }

  private String index(JsonElement value) {
    var entry = value.getAsJsonObject();
    return "new BuiltinContracts.IndexCapability(BuiltinContracts.IndexKind."
        + entry.get("kind").getAsString()
        + ", "
        + type(entry.get("keyType"))
        + ", "
        + type(entry.get("resultType"))
        + ", "
        + intrinsic(entry.get("readIntrinsic"))
        + ", "
        + optional(entry.get("writeIntrinsic"), this::intrinsic)
        + ")";
  }

  private void valueAbi(String name, JsonObject contract) {
    var text = header(name, "");
    for (var entry : contract.entrySet()) {
      if (entry.getValue().isJsonPrimitive()) {
        var value = entry.getValue().getAsJsonPrimitive();
        if (value.isString())
          text.append("  public static final String ")
              .append(constant(entry.getKey()))
              .append(" = \"")
              .append(value.getAsString())
              .append("\";\n");
        else if (value.isNumber())
          text.append("  public static final int ")
              .append(constant(entry.getKey()))
              .append(" = ")
              .append(value)
              .append(";\n");
      }
    }
    finish(name, text);
  }

  private void formatAbi(String domain, JsonObject contract, boolean exception) {
    String name =
        domain.substring(0, 1).toUpperCase(Locale.ROOT)
            + domain.substring(1)
            + (exception ? "ExceptionAbi" : "Abi");
    var variants =
        contract.has("variants")
            ? contract.getAsJsonArray("variants").asList()
            : List.<JsonElement>of();
    var text =
        header(
            name,
            (exception
                    ? "import java.util.Map;\n"
                    : variants.isEmpty() ? "" : "import java.util.List;\n")
                + "import java.util.Set;\n");
    stringConstants(text, contract);
    text.append("  public static final int MODULE_VERSION = ")
        .append(contract.get("moduleVersion"))
        .append(";\n");
    for (var element : contract.getAsJsonArray("fields")) {
      var field = element.getAsJsonObject();
      String fieldName = field.get("name").getAsString();
      text.append("  public static final String FIELD_")
          .append(constant(fieldName))
          .append("_NAME = \"")
          .append(fieldName)
          .append("\";\n");
      text.append("  public static final int FIELD_")
          .append(constant(fieldName))
          .append("_ORDINAL = ")
          .append(field.get("ordinal"))
          .append(";\n");
    }
    for (var variant : variants)
      text.append("  public static final String VALUE_VARIANT_")
          .append(constant(variant.getAsString()))
          .append(" = \"")
          .append(variant.getAsString())
          .append("\";\n");
    text.append("\n  public static final Set<String> INTRINSIC_NAMES =\n      Set.of(\n");
    var names = contract.getAsJsonArray("intrinsicNames");
    for (int i = 0; i < names.size(); i++)
      text.append("          \"")
          .append(names.get(i).getAsString())
          .append(i + 1 == names.size() ? "\");\n" : "\",\n");
    if (!variants.isEmpty()) {
      text.append("  public static final List<String> VALUE_VARIANTS =\n      List.of(\n");
      for (int i = 0; i < variants.size(); i++)
        text.append("          VALUE_VARIANT_")
            .append(constant(variants.get(i).getAsString()))
            .append(i + 1 == variants.size() ? ");\n" : ",\n");
    }
    if (exception) {
      text.append(
          "\n  private static final Map<String, String> OPERATIONS =\n      Map.ofEntries(\n");
      var operations = contract.getAsJsonArray("operations");
      for (int i = 0; i < operations.size(); i++) {
        var entry = operations.get(i).getAsJsonObject();
        text.append("          Map.entry(\"")
            .append(entry.get("platformName").getAsString())
            .append("\", \"")
            .append(entry.get("variant").getAsString())
            .append(i + 1 == operations.size() ? "\"));\n" : "\"),\n");
      }
      text.append("  private static final Map<String, Failure> FAILURES =\n      Map.ofEntries(\n");
      var failures = contract.getAsJsonArray("failures");
      for (int i = 0; i < failures.size(); i++) {
        var entry = failures.get(i).getAsJsonObject();
        text.append("          Map.entry(\"")
            .append(entry.get("platformName").getAsString())
            .append("\", new Failure(\"")
            .append(entry.get("variant").getAsString())
            .append("\", \"")
            .append(entry.get("code").getAsString())
            .append(i + 1 == failures.size() ? "\")));\n" : "\")),\n");
      }
      text.append(
              "\n  public static String operationVariant(String platformName) {\n    String variant = OPERATIONS.get(platformName);\n    if (variant == null) throw new IllegalArgumentException(\"unknown ")
          .append(domain)
          .append(
              " operation \" + platformName);\n    return variant;\n  }\n\n  public static Failure failure(String platformName) {\n    Failure failure = FAILURES.get(platformName);\n    if (failure == null) throw new IllegalArgumentException(\"unknown ")
          .append(domain)
          .append(
              " failure \" + platformName);\n    return failure;\n  }\n\n  public record Failure(String variant, String code) {}\n");
    }
    finish(name, text);
  }

  private static StringBuilder header(String name, String imports) {
    return new StringBuilder("package dev.w0fv1.norm.abi;\n\n")
        .append(imports)
        .append(imports.isEmpty() ? "" : "\n")
        .append("public final class ")
        .append(name)
        .append(" {\n");
  }

  private void finish(String name, StringBuilder text) {
    outputs.put(name, text.append("\n  private ").append(name).append("() {}\n}\n").toString());
  }

  private static void stringConstants(StringBuilder text, JsonObject contract) {
    for (var entry : contract.entrySet()) {
      if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString())
        text.append("  public static final String ")
            .append(constant(entry.getKey()))
            .append(" = \"")
            .append(entry.getValue().getAsString())
            .append("\";\n");
    }
  }

  private static String constant(String name) {
    return name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
  }

  private static String literal(Object value) {
    String json = JSON.toJson(value);
    var escaped = new StringBuilder();
    for (int i = 0; i < json.length(); i++) {
      char character = json.charAt(i);
      if (character > 127) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
      else escaped.append(character);
    }
    return escaped.toString();
  }

  private static String list(JsonElement value, Function<JsonElement, String> render) {
    return value.getAsJsonArray().asList().stream()
        .map(render)
        .collect(Collectors.joining(", ", "java.util.List.of(", ")"));
  }

  private static String optional(JsonElement value, Function<JsonElement, String> render) {
    return value == null || value.isJsonNull()
        ? "java.util.Optional.empty()"
        : "java.util.Optional.of(" + render.apply(value) + ")";
  }
}
