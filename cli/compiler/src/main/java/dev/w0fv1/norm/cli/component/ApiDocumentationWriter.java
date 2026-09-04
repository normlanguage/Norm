package dev.w0fv1.norm.cli.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.w0fv1.norm.documentation.ModuleDocumentation;
import dev.w0fv1.norm.value.BuildMetadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApiDocumentationWriter {
  private static final String MODULE_SCHEMA =
      "https://normlanguage.github.io/Norm/schemas/module-api-v1.json";
  private static final String FILE_SCHEMA =
      "https://normlanguage.github.io/Norm/schemas/file-api-v1.json";
  private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  public ApiDocumentationWriter() {}

  public void write(ModuleDocumentation documentation, Path outputDirectory) throws IOException {
    Path output = outputDirectory.toAbsolutePath().normalize();
    Path parent = output.getParent();
    if (parent == null) throw new IOException("documentation output requires a parent directory");
    Files.createDirectories(parent);
    Path temporary =
        parent.resolve("." + output.getFileName() + ".tmp-" + UUID.randomUUID()).normalize();
    Path backup =
        parent.resolve("." + output.getFileName() + ".previous-" + UUID.randomUUID()).normalize();
    try {
      Files.createDirectory(temporary);
      Files.writeString(
          temporary.resolve("module.api.json"),
          gson.toJson(module(documentation)) + System.lineSeparator(),
          StandardCharsets.UTF_8);
      for (ModuleDocumentation.File file : documentation.files()) {
        Path target = temporary.resolve(file.documentPath()).normalize();
        if (!target.startsWith(temporary)) {
          throw new IOException("documentation path escapes the output directory");
        }
        Files.createDirectories(target.getParent());
        Files.writeString(
            target,
            gson.toJson(file(documentation, file)) + System.lineSeparator(),
            StandardCharsets.UTF_8);
      }
      if (Files.exists(output)) {
        requireManagedOutput(output);
        Files.move(output, backup);
      }
      try {
        Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
      } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
        Files.move(temporary, output);
      }
      deleteTree(backup);
    } catch (IOException | RuntimeException failure) {
      if (Files.exists(backup) && !Files.exists(output)) Files.move(backup, output);
      throw failure;
    } finally {
      deleteTree(temporary);
      deleteTree(backup);
    }
  }

  private JsonObject module(ModuleDocumentation documentation) {
    JsonObject value = new JsonObject();
    value.addProperty("$schema", MODULE_SCHEMA);
    value.addProperty("schemaVersion", 1);
    value.addProperty("kind", "module");
    JsonObject generator = new JsonObject();
    generator.addProperty("name", "norm");
    generator.addProperty("version", BuildMetadata.VERSION);
    value.add("generator", generator);
    value.add("module", moduleCoordinate(documentation));
    value.addProperty("descriptionFormat", "commonmark");
    value.add("tree", tree(documentation.files()));
    return value;
  }

  private JsonObject file(
      ModuleDocumentation documentation, ModuleDocumentation.File documentationFile) {
    JsonObject value = new JsonObject();
    value.addProperty("$schema", FILE_SCHEMA);
    value.addProperty("schemaVersion", 1);
    value.addProperty("kind", "file");
    value.add("module", moduleCoordinate(documentation));
    JsonObject source = new JsonObject();
    source.addProperty("path", documentationFile.sourcePath());
    value.add("source", source);
    value.addProperty("package", documentationFile.packageName());
    value.addProperty("exported", documentationFile.exported());
    documentationFile.document().ifPresent(document -> value.add("document", document(document)));
    JsonArray declarations = new JsonArray();
    documentationFile.declarations().forEach(item -> declarations.add(declaration(item)));
    value.add("declarations", declarations);
    return value;
  }

  private static JsonObject moduleCoordinate(ModuleDocumentation documentation) {
    JsonObject module = new JsonObject();
    module.addProperty("name", documentation.module().name());
    module.addProperty("version", documentation.module().version());
    return module;
  }

  private JsonObject declaration(ModuleDocumentation.Declaration declaration) {
    JsonObject value = new JsonObject();
    value.addProperty("kind", declaration.kind());
    value.addProperty("id", declaration.id());
    value.addProperty("name", declaration.name());
    value.addProperty("signature", declaration.signature());
    value.addProperty("visibility", declaration.visibility());
    value.add("source", source(declaration.source()));
    JsonArray typeParameters = new JsonArray();
    declaration.typeParameters().forEach(parameter -> typeParameters.add(typeParameter(parameter)));
    value.add("typeParameters", typeParameters);
    JsonArray parameters = new JsonArray();
    declaration.parameters().forEach(parameter -> parameters.add(parameter(parameter)));
    value.add("parameters", parameters);
    declaration.returns().ifPresent(type -> value.add("returns", type(type)));
    declaration.type().ifPresent(type -> value.add("type", type(type)));
    declaration.document().ifPresent(document -> value.add("document", document(document)));
    JsonArray members = new JsonArray();
    declaration.members().forEach(member -> members.add(declaration(member)));
    value.add("members", members);
    return value;
  }

  private JsonObject document(ModuleDocumentation.Document document) {
    JsonObject value = new JsonObject();
    value.addProperty("description", document.description());
    value.add("types", references(document.types()));
    value.add("functions", references(document.functions()));
    value.add("fields", references(document.fields()));
    return value;
  }

  private static JsonArray references(List<ModuleDocumentation.Reference> references) {
    JsonArray values = new JsonArray();
    for (ModuleDocumentation.Reference reference : references) {
      JsonObject value = new JsonObject();
      value.addProperty("kind", reference.kind());
      value.addProperty("target", reference.target());
      value.addProperty("display", reference.display());
      values.add(value);
    }
    return values;
  }

  private JsonObject parameter(ModuleDocumentation.Parameter parameter) {
    JsonObject value = new JsonObject();
    value.addProperty("name", parameter.name());
    value.add("type", type(parameter.type()));
    parameter.document().ifPresent(document -> value.add("document", document(document)));
    return value;
  }

  private JsonObject typeParameter(ModuleDocumentation.TypeParameter parameter) {
    JsonObject value = new JsonObject();
    value.addProperty("name", parameter.name());
    parameter.upperBound().ifPresent(bound -> value.add("upperBound", type(bound)));
    parameter.defaultType().ifPresent(defaultType -> value.add("defaultType", type(defaultType)));
    return value;
  }

  private JsonObject type(ModuleDocumentation.Type type) {
    JsonObject value = new JsonObject();
    value.addProperty("kind", type.kind());
    value.addProperty("identity", type.identity());
    value.addProperty("name", type.name());
    value.addProperty("display", type.display());
    value.addProperty("nullable", type.nullable());
    JsonArray arguments = new JsonArray();
    type.arguments().forEach(argument -> arguments.add(type(argument)));
    value.add("arguments", arguments);
    return value;
  }

  private static JsonObject source(ModuleDocumentation.SourceRange source) {
    JsonObject value = new JsonObject();
    value.add("start", position(source.start()));
    value.add("end", position(source.end()));
    return value;
  }

  private static JsonObject position(ModuleDocumentation.Position position) {
    JsonObject value = new JsonObject();
    value.addProperty("line", position.line());
    value.addProperty("column", position.column());
    return value;
  }

  private static JsonArray tree(List<ModuleDocumentation.File> files) {
    Directory root = new Directory("");
    for (ModuleDocumentation.File file : files) root.add(file);
    return root.children();
  }

  private static void requireManagedOutput(Path output) throws IOException {
    Path manifest = output.resolve("module.api.json");
    if (!Files.isRegularFile(manifest)) {
      throw new IOException("documentation output already exists and is not a generated API root");
    }
    JsonObject value;
    try {
      value = com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
    } catch (RuntimeException exception) {
      throw new IOException("documentation output contains an invalid module.api.json", exception);
    }
    if (!value.has("kind") || !value.get("kind").getAsString().equals("module")) {
      throw new IOException("documentation output is not a generated API root");
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }

  private static final class Directory {
    private final String name;
    private final Map<String, Directory> directories = new LinkedHashMap<>();
    private final List<ModuleDocumentation.File> files = new ArrayList<>();

    private Directory(String name) {
      this.name = name;
    }

    private void add(ModuleDocumentation.File file) {
      String[] segments = file.documentPath().split("/");
      Directory directory = this;
      for (int index = 0; index + 1 < segments.length; index++) {
        directory = directory.directories.computeIfAbsent(segments[index], Directory::new);
      }
      directory.files.add(file);
    }

    private JsonArray children() {
      JsonArray values = new JsonArray();
      directories.values().stream()
          .sorted(Comparator.comparing(value -> value.name))
          .forEach(
              directory -> {
                JsonObject value = new JsonObject();
                value.addProperty("kind", "directory");
                value.addProperty("name", directory.name);
                value.add("children", directory.children());
                values.add(value);
              });
      files.stream()
          .sorted(Comparator.comparing(ModuleDocumentation.File::documentPath))
          .forEach(
              file -> {
                JsonObject value = new JsonObject();
                value.addProperty("kind", "file");
                String filename = Path.of(file.sourcePath()).getFileName().toString();
                value.addProperty(
                    "name", filename.substring(0, filename.length() - ".norm".length()));
                value.addProperty("source", file.sourcePath());
                value.addProperty("document", file.documentPath());
                value.addProperty("package", file.packageName());
                value.addProperty("exported", file.exported());
                values.add(value);
              });
      return values;
    }
  }
}
