package dev.w0fv1.norm.packaging;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ToolchainArtifactCatalogGenerator {
  public record Artifact(Path file, String coordinate) {}

  public record Input(
      List<Artifact> artifacts,
      List<String> roots,
      Map<String, List<String>> dependencies,
      Map<String, List<String>> purposes,
      Map<String, List<String>> mergedModules) {}

  private ToolchainArtifactCatalogGenerator() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 2)
      throw new IllegalArgumentException("Expected catalog inputs and output file");
    JsonObject source;
    try (var reader = Files.newBufferedReader(Path.of(arguments[0]), StandardCharsets.UTF_8)) {
      source = JsonParser.parseReader(reader).getAsJsonObject();
    }
    List<Artifact> artifacts = new ArrayList<>();
    source
        .getAsJsonArray("artifacts")
        .forEach(
            value -> {
              var item = value.getAsJsonObject();
              artifacts.add(
                  new Artifact(
                      Path.of(item.get("path").getAsString()),
                      item.get("coordinate").getAsString()));
            });
    List<String> roots =
        source.getAsJsonArray("roots").asList().stream().map(value -> value.getAsString()).toList();
    Map<String, List<String>> dependencies = new TreeMap<>();
    source
        .getAsJsonObject("dependencies")
        .entrySet()
        .forEach(
            entry ->
                dependencies.put(
                    entry.getKey(),
                    entry.getValue().getAsJsonArray().asList().stream()
                        .map(value -> value.getAsString())
                        .toList()));
    Map<String, List<String>> purposes = new TreeMap<>();
    source
        .getAsJsonObject("purposes")
        .entrySet()
        .forEach(
            entry ->
                purposes.put(
                    entry.getKey(),
                    entry.getValue().getAsJsonArray().asList().stream()
                        .map(value -> value.getAsString())
                        .toList()));
    Map<String, List<String>> mergedModules = new TreeMap<>();
    source
        .getAsJsonObject("mergedModules")
        .entrySet()
        .forEach(
            entry ->
                mergedModules.put(
                    entry.getKey(),
                    entry.getValue().getAsJsonArray().asList().stream()
                        .map(value -> value.getAsString())
                        .toList()));
    generate(
        new Input(artifacts, roots, dependencies, purposes, mergedModules), Path.of(arguments[1]));
  }

  public static void generate(Input input, Path output) throws IOException {
    Map<String, Artifact> artifacts = new TreeMap<>();
    for (Artifact artifact : input.artifacts()) {
      if (!Files.isRegularFile(artifact.file()))
        throw new IllegalArgumentException("Toolchain artifact is missing: " + artifact.file());
      String name = artifact.file().getFileName().toString();
      if (artifacts.putIfAbsent(name, artifact) != null)
        throw new IllegalArgumentException("Duplicate toolchain artifact filename: " + name);
      if (!input.dependencies().containsKey(artifact.coordinate()))
        throw new IllegalArgumentException(
            "Toolchain artifact is absent from graph: " + artifact.coordinate());
    }
    for (var entry : input.dependencies().entrySet()) {
      for (String dependency : entry.getValue()) {
        if (!input.dependencies().containsKey(dependency))
          throw new IllegalArgumentException("Toolchain dependency is absent: " + dependency);
      }
    }
    for (String root : input.roots()) {
      if (!input.dependencies().containsKey(root))
        throw new IllegalArgumentException("Toolchain root is absent: " + root);
    }
    for (String purpose : List.of("execution", "hosted", "tooling")) {
      for (String root : input.purposes().getOrDefault(purpose, List.of())) {
        if (!input.roots().contains(root))
          throw new IllegalArgumentException("Toolchain purpose is not a root: " + root);
      }
    }

    JsonArray resolved = new JsonArray();
    for (var item : artifacts.entrySet()) {
      Artifact artifact = item.getValue();
      String[] coordinate = artifact.coordinate().split(":", -1);
      if (coordinate.length != 3 || Arrays.stream(coordinate).anyMatch(String::isBlank))
        throw new IllegalArgumentException(
            "Invalid toolchain coordinate: " + artifact.coordinate());
      Set<String> components = new HashSet<>();
      components.add(artifact.coordinate());
      for (String module :
          input.mergedModules().getOrDefault(coordinate[0] + ":" + coordinate[1], List.of())) {
        List<String> matches =
            input.dependencies().keySet().stream()
                .filter(key -> key.substring(0, key.lastIndexOf(':')).equals(module))
                .toList();
        if (matches.size() != 1)
          throw new IllegalArgumentException(
              "Merged toolchain component is not uniquely resolved: " + module);
        components.add(matches.get(0));
      }
      JsonObject entry = new JsonObject();
      entry.addProperty("file", item.getKey());
      entry.addProperty("group", coordinate[0]);
      entry.addProperty("artifact", coordinate[1]);
      entry.addProperty("version", coordinate[2]);
      JsonArray owned = new JsonArray();
      components.stream().sorted().forEach(owned::add);
      entry.add("components", owned);
      JsonObject storage = new JsonObject();
      if (Files.isSymbolicLink(artifact.file())) {
        Path target = Files.readSymbolicLink(artifact.file());
        if (!target.isAbsolute() || !target.equals(target.normalize()))
          throw new IllegalArgumentException("Invalid system toolchain target: " + target);
        var modules = ModuleFinder.of(artifact.file()).findAll();
        if (modules.size() != 1)
          throw new IllegalArgumentException("Expected one system module: " + artifact.file());
        ModuleDescriptor descriptor = modules.iterator().next().descriptor();
        String moduleName = item.getKey().replaceFirst("\\.jar$", "");
        if (!descriptor.name().equals(moduleName))
          throw new IllegalArgumentException(
              "System toolchain module does not match file: " + artifact.file());
        storage.addProperty("kind", "system");
        storage.addProperty("target", target.toString());
        storage.addProperty("automatic", descriptor.isAutomatic());
        JsonArray moduleRequires = new JsonArray();
        descriptor.requires().stream()
            .filter(
                requirement ->
                    !requirement.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC))
            .sorted(java.util.Comparator.comparing(ModuleDescriptor.Requires::name))
            .forEach(
                requirement -> {
                  JsonObject requirementEntry = new JsonObject();
                  requirementEntry.addProperty("name", requirement.name());
                  requirementEntry.addProperty(
                      "transitive",
                      requirement
                          .modifiers()
                          .contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE));
                  moduleRequires.add(requirementEntry);
                });
        storage.add("moduleRequires", moduleRequires);
      } else {
        MessageDigest digest;
        try {
          digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
          throw new IllegalStateException(exception);
        }
        try (var stream = new DigestInputStream(Files.newInputStream(artifact.file()), digest)) {
          stream.transferTo(java.io.OutputStream.nullOutputStream());
        }
        storage.addProperty("kind", "sealed");
        storage.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
      }
      entry.add("storage", storage);
      resolved.add(entry);
    }
    JsonObject catalog = new JsonObject();
    catalog.addProperty("schemaVersion", 2);
    catalog.add("artifacts", resolved);
    JsonArray roots = new JsonArray();
    input.roots().forEach(roots::add);
    catalog.add("roots", roots);
    JsonObject dependencies = new JsonObject();
    new TreeMap<>(input.dependencies())
        .forEach(
            (coordinate, edges) -> {
              JsonArray values = new JsonArray();
              edges.forEach(values::add);
              dependencies.add(coordinate, values);
            });
    catalog.add("dependencies", dependencies);
    JsonObject purposes = new JsonObject();
    for (String purpose : List.of("execution", "hosted", "tooling")) {
      JsonArray values = new JsonArray();
      input.purposes().getOrDefault(purpose, List.of()).forEach(values::add);
      purposes.add(purpose, values);
    }
    catalog.add("purposes", purposes);
    Files.createDirectories(output.toAbsolutePath().getParent());
    Files.writeString(
        output,
        new GsonBuilder().disableHtmlEscaping().create().toJson(catalog) + "\n",
        StandardCharsets.UTF_8);
  }
}
