package dev.w0fv1.norm.cli.component;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class NativeBuildReport implements AutoCloseable, Consumer<String> {
  private final Path directory;
  private final BufferedWriter log;
  private final Consumer<String> output;

  private NativeBuildReport(Path directory, Consumer<String> output) throws IOException {
    this.directory = directory;
    this.output = output;
    try (var catalog = NativeBuildReport.class.getResourceAsStream("/toolchain-artifacts.json")) {
      if (catalog == null) throw new IOException("Norm toolchain artifact catalog is unavailable");
      Files.copy(catalog, directory.resolve("toolchain-artifacts.json"));
    }
    log = Files.newBufferedWriter(directory.resolve("build.log"));
  }

  static NativeBuildReport create(Path executable, Consumer<String> output) throws IOException {
    Path target = executable.toAbsolutePath().normalize();
    Path root = target.getParent().resolve(".norm/build-reports").resolve(target.getFileName());
    Files.createDirectories(root);
    return new NativeBuildReport(Files.createTempDirectory(root, "run-"), output);
  }

  Path directory() {
    return directory;
  }

  void buildInputs(List<String> arguments, List<Path> classpath, Path archive, Path launcher)
      throws IOException {
    var manifest = new JsonObject();
    manifest.addProperty("schemaVersion", 1);
    var options = new com.google.gson.JsonArray();
    arguments.forEach(options::add);
    manifest.add("arguments", options);
    var entries = new com.google.gson.JsonArray();
    for (var path : classpath) entries.add(input(path));
    manifest.add("classpath", entries);
    manifest.add("archive", input(archive));
    manifest.add("launcher", input(launcher));
    Files.writeString(
        directory.resolve("build-inputs.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n");
  }

  private static JsonObject input(Path input) throws IOException {
    Path path = input.toAbsolutePath().normalize();
    var entry = new JsonObject();
    entry.addProperty("path", path.toString());
    var files = new com.google.gson.JsonArray();
    boolean directory = Files.isDirectory(path);
    entry.addProperty("kind", directory ? "directory" : "file");
    List<Path> contents;
    if (directory) {
      try (var tree = Files.walk(path, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
        contents = tree.filter(Files::isRegularFile).sorted().toList();
      }
    } else {
      contents = List.of(path);
    }
    for (var file : contents) {
      var item = new JsonObject();
      item.addProperty(
          "path",
          directory
              ? path.relativize(file).toString().replace('\\', '/')
              : file.getFileName().toString());
      item.addProperty("sha256", Sha256Digest.compute(file).value());
      item.addProperty("bytes", Files.size(file));
      files.add(item);
    }
    entry.add("files", files);
    return entry;
  }

  void coreRetention(dev.w0fv1.norm.core.CoreReachability.Analysis analysis) throws IOException {
    var groups = new JsonObject();
    for (var group : analysis.artifact().program().groups()) {
      var cause = analysis.causes().get(group.id());
      var entry = new JsonObject();
      entry.addProperty("reason", cause.kind().name());
      cause.dependency().ifPresent(kind -> entry.addProperty("dependency", kind.name()));
      cause.source().ifPresent(source -> entry.addProperty("source", source.toString()));
      var names = new com.google.gson.JsonArray();
      analysis.artifact().namespace().bindings().stream()
          .filter(binding -> binding.definition().group().equals(group.id()))
          .map(
              binding ->
                  binding.packageName()
                      + "."
                      + binding.ownerName().map(owner -> owner + ".").orElse("")
                      + binding.name())
          .distinct()
          .sorted()
          .forEach(names::add);
      entry.add("names", names);
      var intrinsicNames = new com.google.gson.JsonArray();
      dev.w0fv1.norm.core.CoreReachability.intrinsics(group)
          .forEach(intrinsic -> intrinsicNames.add(intrinsic.name()));
      entry.add("intrinsics", intrinsicNames);
      groups.add(group.id().toString(), entry);
    }
    var manifest = new JsonObject();
    manifest.addProperty("schemaVersion", 1);
    manifest.add("groups", groups);
    Files.writeString(
        directory.resolve("core-retention.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n");
  }

  void javaArtifacts(List<dev.w0fv1.norm.jvm.ResolvedJarArtifact> artifacts) throws IOException {
    var entries = new com.google.gson.JsonArray();
    for (var artifact :
        artifacts.stream()
            .sorted(java.util.Comparator.comparing(value -> value.identity().canonical()))
            .toList()) {
      var actual = Sha256Digest.compute(artifact.file());
      if (!actual.equals(artifact.content())) {
        throw new IOException(
            "Selected Java artifact content changed: "
                + artifact.identity().canonical()
                + " at "
                + artifact.file());
      }
      var entry = new JsonObject();
      entry.addProperty("identity", artifact.identity().canonical());
      entry.addProperty("path", artifact.file().toString());
      entry.addProperty("sha256", actual.value());
      entry.addProperty("bytes", Files.size(artifact.file()));
      entries.add(entry);
    }
    var manifest = new JsonObject();
    manifest.addProperty("schemaVersion", 1);
    manifest.add("artifacts", entries);
    Files.writeString(
        directory.resolve("java-artifacts.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n");
  }

  void applicationMethods(
      java.util.Map<
              dev.w0fv1.norm.core.DefinitionId,
              dev.w0fv1.norm.jvm.JavaApplicationMethodIndex.Target>
          methods)
      throws IOException {
    var mapping = new JsonObject();
    new java.util.TreeMap<>(methods)
        .forEach(
            (id, target) -> {
              var value = new JsonObject();
              value.addProperty("owner", target.owner());
              value.addProperty("name", target.name());
              value.addProperty("descriptor", target.descriptor());
              mapping.add(id.toString(), value);
            });
    Files.writeString(
        directory.resolve("application-methods.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(mapping) + "\n");
  }

  List<String> arguments() {
    return List.of(
        "-H:+GenerateBuildArtifactsFile",
        "-H:+DiagnosticsMode",
        "-H:DiagnosticsDir=" + directory.resolve("analysis"),
        "-H:+PrintAnalysisCallTree",
        "-H:+PrintImageHeapPartitionSizes",
        "-H:PrintAnalysisCallTreeType=CSV",
        "-H:BuildOutputJSONFile=" + directory.resolve("build-output.json"),
        "-H:DashboardDump=" + directory.resolve("dashboard"),
        "-H:+DashboardCode",
        "-H:+DashboardHeap",
        "-H:+DashboardJson",
        "-H:-DashboardBgv");
  }

  void complete(Path executable, List<Path> delivered) throws IOException {
    Path statistics = directory.resolve("build-output.json");
    JsonObject raw;
    try (var reader = Files.newBufferedReader(statistics)) {
      raw = JsonParser.parseReader(reader).getAsJsonObject();
    } catch (RuntimeException exception) {
      throw new IOException("Invalid Native Image statistics: " + statistics, exception);
    }
    var size = new JsonObject();
    size.addProperty("schemaVersion", 1);
    size.addProperty("executable", executable.toAbsolutePath().normalize().toString());
    size.addProperty("sha256", Sha256Digest.compute(executable).value());
    size.addProperty("executableBytes", Files.size(executable));
    size.addProperty("imageBytes", metric(raw, "image_details", "total_bytes"));
    long code = metric(raw, "image_details", "code_area", "bytes");
    long heap = metric(raw, "image_details", "image_heap", "bytes");
    long methods = metric(raw, "analysis_results", "methods", "reachable");
    size.addProperty("codeBytes", code);
    size.addProperty("heapBytes", heap);
    size.addProperty("reachableMethods", methods);
    size.addProperty("reflectionMethods", metric(raw, "analysis_results", "methods", "reflection"));
    size.addProperty("reachableTypes", metric(raw, "analysis_results", "types", "reachable"));
    size.addProperty("reflectionTypes", metric(raw, "analysis_results", "types", "reflection"));
    var runtimeFiles = new com.google.gson.JsonArray();
    long deliveryBytes = 0;
    Path parent = executable.toAbsolutePath().normalize().getParent();
    for (Path file : delivered) {
      Path normalized = file.toAbsolutePath().normalize();
      if (!normalized.startsWith(parent))
        throw new IOException("Runtime artifact is outside delivery: " + file);
      long bytes = Files.size(normalized);
      var item = new JsonObject();
      item.addProperty("path", parent.relativize(normalized).toString().replace('\\', '/'));
      item.addProperty("bytes", bytes);
      item.addProperty("sha256", Sha256Digest.compute(normalized).value());
      runtimeFiles.add(item);
      deliveryBytes = Math.addExact(deliveryBytes, bytes);
    }
    size.add("runtimeFiles", runtimeFiles);
    size.addProperty("deliveryBytes", deliveryBytes);
    Files.writeString(
        directory.resolve("size.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(size));
    accept(
        String.format(
            Locale.ROOT,
            "Native size: %.2f MiB file; %.2f MiB code; %.2f MiB image heap; %d reachable methods",
            Files.size(executable) / 1048576.0,
            code / 1048576.0,
            heap / 1048576.0,
            methods));
    accept("Build report: " + directory);
    accept(
        String.format(
            Locale.ROOT,
            "Native delivery: %.2f MiB across %d runtime file(s)",
            deliveryBytes / 1048576.0,
            delivered.size()));
  }

  private static long metric(JsonObject raw, String... path) throws IOException {
    try {
      JsonElement value = raw;
      for (String component : path) value = value.getAsJsonObject().get(component);
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
        throw new IllegalArgumentException("expected integer");
      }
      long result = value.getAsBigDecimal().longValueExact();
      if (result < 0) throw new IllegalArgumentException("expected nonnegative integer");
      return result;
    } catch (RuntimeException exception) {
      throw new IOException("Invalid Native Image metric: " + String.join(".", path), exception);
    }
  }

  @Override
  public void accept(String message) {
    try {
      log.write(message);
      log.newLine();
      log.flush();
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot write native build log: " + directory, exception);
    }
    output.accept(message);
  }

  @Override
  public void close() throws IOException {
    log.close();
  }
}
