package dev.w0fv1.norm.cli.component;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.jvm.JarArtifactOwnership;
import dev.w0fv1.norm.jvm.JarDependencyEdge;
import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
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
      if (manifest.get("schemaVersion").getAsInt() != 1)
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
        var entry =
            new Artifact(
                new MavenArtifactCoordinate(
                    artifact.get("group").getAsString(),
                    artifact.get("artifact").getAsString(),
                    artifact.get("version").getAsString()),
                new Sha256Digest(artifact.get("sha256").getAsString()),
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
      artifacts.forEach(
          (name, artifact) -> {
            if (retained.contains(artifact.coordinate().notation())) required.add(name);
          });
      var unmanaged = new ArrayList<Path>();
      var physical = new LinkedHashMap<String, ResolvedJarArtifact>();
      var ownership = new ArrayList<JarArtifactOwnership>();
      for (Path path : paths) {
        var artifact = artifacts.get(path.getFileName().toString());
        if (artifact == null) {
          unmanaged.add(path);
          continue;
        }
        if (!Sha256Digest.compute(path).equals(artifact.content()))
          throw new IOException("Toolchain artifact content mismatch: " + path);
        if (retained.contains(artifact.coordinate().notation())) {
          required.remove(path.getFileName().toString());
          var owner =
              new ResolvedJarArtifact(
                  new MavenJarIdentity(artifact.coordinate()), path, artifact.content());
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

  private record Artifact(
      MavenArtifactCoordinate coordinate, Sha256Digest content, Set<String> components) {}
}
