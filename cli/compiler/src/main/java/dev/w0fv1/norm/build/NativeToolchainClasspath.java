package dev.w0fv1.norm.build;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.jvm.JarArtifactOwnership;
import dev.w0fv1.norm.jvm.JarDependencyEdge;
import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NativeToolchainClasspath {
  private NativeToolchainClasspath() {}

  static Plan plan(Path catalog, List<Path> paths) throws IOException {
    try (var reader = Files.newBufferedReader(catalog)) {
      return plan(JsonParser.parseReader(reader).getAsJsonObject(), paths);
    }
  }

  static Plan plan(JsonObject manifest, List<Path> paths) throws IOException {
    try {
      if (manifest.get("schemaVersion").getAsInt() != 2)
        throw new IOException("Unsupported toolchain artifact catalog");
      var graph = manifest.getAsJsonObject("dependencies");
      var pending = new ArrayDeque<String>();
      for (String purpose : List.of("execution", "hosted"))
        manifest
            .getAsJsonObject("purposes")
            .getAsJsonArray(purpose)
            .forEach(value -> pending.add(value.getAsString()));
      var roots = List.copyOf(pending);
      var retained = new HashSet<String>();
      var artifacts = new HashMap<String, Artifact>();
      var owners = new HashMap<String, Artifact>();
      var required = new HashSet<String>();
      for (var value : manifest.getAsJsonArray("artifacts")) {
        var artifact = value.getAsJsonObject();
        String name = artifact.get("file").getAsString();
        var storage = artifact.getAsJsonObject("storage");
        Storage identity =
            switch (storage.get("kind").getAsString()) {
              case "sealed" -> new Sealed(new Sha256Digest(storage.get("sha256").getAsString()));
              case "system" ->
                  new System(
                      Path.of(storage.get("target").getAsString()),
                      storage.get("automatic").getAsBoolean(),
                      storage.getAsJsonArray("moduleRequires").asList().stream()
                          .map(
                              requirement ->
                                  new ModuleRequirement(
                                      requirement.getAsJsonObject().get("name").getAsString(),
                                      requirement
                                          .getAsJsonObject()
                                          .get("transitive")
                                          .getAsBoolean()))
                          .toList());
              default -> throw new IOException("Invalid toolchain storage: " + name);
            };
        var entry =
            new Artifact(
                new MavenArtifactCoordinate(
                    artifact.get("group").getAsString(),
                    artifact.get("artifact").getAsString(),
                    artifact.get("version").getAsString()),
                identity,
                artifact.getAsJsonArray("components").asList().stream()
                    .map(com.google.gson.JsonElement::getAsString)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        if (!entry.components().contains(entry.coordinate().notation()))
          throw new IOException("Toolchain artifact does not own its primary component: " + name);
        for (String component : entry.components()) {
          if (owners.putIfAbsent(component, entry) != null)
            throw new IOException("Toolchain component has multiple owners: " + component);
        }
        if (artifacts.putIfAbsent(name, entry) != null)
          throw new IOException("Duplicate toolchain artifact: " + name);
      }
      while (!pending.isEmpty()) {
        String coordinate = pending.removeFirst();
        if (!retained.add(coordinate)) continue;
        if (!graph.has(coordinate))
          throw new IOException("Toolchain dependency is absent: " + coordinate);
        graph.getAsJsonArray(coordinate).forEach(value -> pending.add(value.getAsString()));
        var owner = owners.get(coordinate);
        if (owner != null) pending.addAll(owner.components());
      }
      for (var item : artifacts.entrySet()) {
        String name = item.getKey();
        Artifact artifact = item.getValue();
        if (retained.contains(artifact.coordinate().notation())) required.add(name);
      }
      var unmanaged = new ArrayList<Path>();
      var physical = new LinkedHashMap<String, ResolvedJarArtifact>();
      var ownership = new ArrayList<JarArtifactOwnership>();
      for (Path path : paths) {
        var artifact = artifacts.get(path.getFileName().toString());
        if (artifact == null) {
          unmanaged.add(path);
          continue;
        }
        Sha256Digest content;
        if (artifact.storage() instanceof Sealed sealed) {
          if (Files.isSymbolicLink(path))
            throw new IOException("Sealed toolchain artifact is a link: " + path);
          content = Sha256Digest.compute(path);
          if (!content.equals(sealed.content()))
            throw new IOException("Toolchain artifact content mismatch: " + path);
        } else if (artifact.storage() instanceof System system) {
          if (!Files.isSymbolicLink(path) || !Files.readSymbolicLink(path).equals(system.target()))
            throw new IOException("System toolchain target mismatch: " + path);
          FileSnapshot snapshot = FileSnapshot.capture(path);
          var modules = ModuleFinder.of(path).findAll();
          if (modules.size() != 1)
            throw new IOException("Expected one system toolchain module: " + path);
          ModuleDescriptor descriptor = modules.iterator().next().descriptor();
          List<ModuleRequirement> requires =
              descriptor.requires().stream()
                  .filter(
                      requirement ->
                          !requirement
                              .modifiers()
                              .contains(ModuleDescriptor.Requires.Modifier.STATIC))
                  .map(
                      requirement ->
                          new ModuleRequirement(
                              requirement.name(),
                              requirement
                                  .modifiers()
                                  .contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE)))
                  .sorted(java.util.Comparator.comparing(ModuleRequirement::name))
                  .toList();
          if (!descriptor.name().equals(path.getFileName().toString().replaceFirst("\\.jar$", ""))
              || descriptor.isAutomatic() != system.automatic()
              || !requires.equals(system.moduleRequires()))
            throw new IOException("System toolchain module shape changed: " + path);
          snapshot.verify();
          if (!Files.readSymbolicLink(path).equals(system.target()))
            throw new IOException("System toolchain target changed: " + path);
          content = snapshot.content();
        } else throw new IOException("Invalid toolchain storage: " + path);
        if (retained.contains(artifact.coordinate().notation())) {
          required.remove(path.getFileName().toString());
          var owner =
              new ResolvedJarArtifact(new MavenJarIdentity(artifact.coordinate()), path, content);
          artifact.components().forEach(component -> physical.put(component, owner));
          if (artifact.components().size() > 1) {
            var components = new HashSet<MavenArtifactCoordinate>();
            for (String component : artifact.components()) {
              String[] coordinate = component.split(":", 3);
              components.add(
                  new MavenArtifactCoordinate(coordinate[0], coordinate[1], coordinate[2]));
            }
            ownership.add(new JarArtifactOwnership(owner, components));
          }
        }
      }
      if (!required.isEmpty())
        throw new IOException(
            "Required toolchain artifacts are absent: " + required.stream().sorted().toList());
      var edges = new LinkedHashSet<JarDependencyEdge>();
      for (var component : physical.entrySet()) {
        var dependencies =
            graph.getAsJsonArray(component.getKey()).asList().stream()
                .map(com.google.gson.JsonElement::getAsString)
                .toList();
        for (var target : physicalTargets(dependencies, graph, physical)) {
          if (!component.getValue().identity().equals(target.identity()))
            edges.add(new JarDependencyEdge(component.getValue().identity(), target.identity()));
        }
      }
      var files = physical.values().stream().distinct().toList();
      var graphs =
          physicalTargets(roots, graph, physical).stream()
              .map(root -> new ResolvedJarGraph(root, files, List.copyOf(edges)))
              .toList();
      return new Plan(unmanaged, graphs, ownership);
    } catch (RuntimeException exception) {
      throw new IOException("Invalid toolchain artifact catalog", exception);
    }
  }

  private static Set<ResolvedJarArtifact> physicalTargets(
      List<String> components, JsonObject graph, Map<String, ResolvedJarArtifact> physical)
      throws IOException {
    var pending = new ArrayDeque<>(components);
    var visited = new HashSet<String>();
    var targets = new LinkedHashSet<ResolvedJarArtifact>();
    while (!pending.isEmpty()) {
      String component = pending.removeFirst();
      if (!visited.add(component)) continue;
      var owner = physical.get(component);
      if (owner != null) targets.add(owner);
      else {
        if (!graph.has(component))
          throw new IOException("Toolchain dependency is absent: " + component);
        graph.getAsJsonArray(component).forEach(value -> pending.add(value.getAsString()));
      }
    }
    return targets;
  }

  record Plan(
      List<Path> unmanaged, List<ResolvedJarGraph> graphs, List<JarArtifactOwnership> ownership) {
    Plan {
      unmanaged = List.copyOf(unmanaged);
      graphs = List.copyOf(graphs);
      ownership = List.copyOf(ownership);
    }

    void validate(List<ResolvedJarArtifact> selected) throws IOException {
      try {
        ownership.forEach(owner -> owner.validate(selected));
      } catch (IllegalArgumentException conflict) {
        throw new IOException(conflict.getMessage(), conflict);
      }
    }
  }

  private sealed interface Storage permits Sealed, System {}

  private record Sealed(Sha256Digest content) implements Storage {}

  private record System(Path target, boolean automatic, List<ModuleRequirement> moduleRequires)
      implements Storage {
    System {
      if (!target.isAbsolute() || !target.equals(target.normalize()))
        throw new IllegalArgumentException("Invalid system toolchain target: " + target);
      moduleRequires = List.copyOf(moduleRequires);
    }
  }

  private record ModuleRequirement(String name, boolean transitive) {}

  private record Artifact(
      MavenArtifactCoordinate coordinate, Storage storage, Set<String> components) {}
}
