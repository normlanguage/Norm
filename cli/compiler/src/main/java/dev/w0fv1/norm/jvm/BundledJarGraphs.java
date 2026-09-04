package dev.w0fv1.norm.jvm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BundledJarGraphs {
  public static final String MANIFEST = "graphs.json";

  private BundledJarGraphs() {}

  public static void write(Path directory, List<ResolvedJarBinding> bindings) throws IOException {
    Path root = normalize(directory);
    Path artifactsDirectory = root.resolve("artifacts");
    Files.createDirectories(artifactsDirectory);
    Map<String, ResolvedJarArtifact> artifacts = new LinkedHashMap<>();
    Map<String, ResolvedJarGraph> graphs = new LinkedHashMap<>();
    for (ResolvedJarBinding binding : bindings) {
      ResolvedJarGraph graph = binding.graph();
      graphs.putIfAbsent(graph.contentId().value(), graph);
      for (ResolvedJarArtifact artifact : graph.artifacts()) {
        artifacts.putIfAbsent(artifact.content().value(), artifact);
      }
    }
    for (ResolvedJarArtifact artifact : artifacts.values()) {
      Path target = artifactsDirectory.resolve(artifact.content().value() + ".jar");
      if (!Files.isRegularFile(target)) {
        Files.copy(artifact.file(), target, StandardCopyOption.REPLACE_EXISTING);
      }
    }
    JsonObject manifest = new JsonObject();
    manifest.addProperty("formatVersion", 1);
    JsonArray entries = new JsonArray();
    for (ResolvedJarGraph graph : graphs.values()) entries.add(graphJson(graph));
    manifest.add("graphs", entries);
    Files.writeString(root.resolve(MANIFEST), manifest.toString(), StandardCharsets.UTF_8);
  }

  public static Map<Sha256Digest, ResolvedJarGraph> read(Path directory) throws IOException {
    Path root = normalize(directory);
    JsonObject manifest;
    try {
      manifest =
          JsonParser.parseString(Files.readString(root.resolve(MANIFEST), StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (manifest.get("formatVersion").getAsInt() != 1) {
        throw new IllegalArgumentException("unsupported bundled JAR graph format");
      }
    } catch (RuntimeException exception) {
      throw new IOException("invalid bundled JAR graph manifest", exception);
    }
    Map<Sha256Digest, ResolvedJarGraph> result = new LinkedHashMap<>();
    for (var value : manifest.getAsJsonArray("graphs")) {
      JsonObject entry = value.getAsJsonObject();
      Map<String, ResolvedJarArtifact> artifacts = new LinkedHashMap<>();
      for (var artifactValue : entry.getAsJsonArray("artifacts")) {
        JsonObject artifactEntry = artifactValue.getAsJsonObject();
        JarArtifactIdentity identity = identity(artifactEntry.get("identity").getAsString());
        Sha256Digest content = Sha256Digest.parse(artifactEntry.get("sha256").getAsString());
        Path file = root.resolve("artifacts").resolve(content.value() + ".jar").normalize();
        if (!file.startsWith(root)
            || !Files.isRegularFile(file)
            || !content.equals(Sha256Digest.compute(file))) {
          throw new IOException("bundled JAR content is unavailable: " + identity.canonical());
        }
        artifacts.put(identity.canonical(), new ResolvedJarArtifact(identity, file, content));
      }
      List<JarDependencyEdge> edges =
          entry.getAsJsonArray("edges").asList().stream()
              .map(
                  edgeValue -> {
                    JsonObject edge = edgeValue.getAsJsonObject();
                    return new JarDependencyEdge(
                        required(artifacts, edge.get("from").getAsString()).identity(),
                        required(artifacts, edge.get("to").getAsString()).identity());
                  })
              .toList();
      ResolvedJarGraph graph =
          new ResolvedJarGraph(
              required(artifacts, entry.get("root").getAsString()),
              List.copyOf(artifacts.values()),
              edges);
      Sha256Digest id = Sha256Digest.parse(entry.get("id").getAsString());
      if (!id.equals(graph.contentId()))
        throw new IOException("bundled JAR graph identity mismatch");
      if (result.putIfAbsent(id, graph) != null) {
        throw new IOException("duplicate bundled JAR graph " + id);
      }
    }
    return Map.copyOf(result);
  }

  private static JsonObject graphJson(ResolvedJarGraph graph) {
    JsonObject result = new JsonObject();
    result.addProperty("id", graph.contentId().value());
    result.addProperty("root", graph.root().identity().canonical());
    JsonArray artifacts = new JsonArray();
    for (ResolvedJarArtifact artifact : graph.artifacts()) {
      JsonObject entry = new JsonObject();
      entry.addProperty("identity", artifact.identity().canonical());
      entry.addProperty("sha256", artifact.content().value());
      artifacts.add(entry);
    }
    result.add("artifacts", artifacts);
    JsonArray edges = new JsonArray();
    for (JarDependencyEdge edge : graph.edges()) {
      JsonObject entry = new JsonObject();
      entry.addProperty("from", edge.from().canonical());
      entry.addProperty("to", edge.to().canonical());
      edges.add(entry);
    }
    result.add("edges", edges);
    return result;
  }

  private static JarArtifactIdentity identity(String canonical) {
    Objects.requireNonNull(canonical, "canonical");
    if (canonical.startsWith("local:")) {
      return new LocalJarIdentity(Sha256Digest.parse(canonical.substring("local:".length())));
    }
    String[] parts = canonical.split(":", -1);
    if (parts.length == 4 && parts[0].equals("maven")) {
      return new MavenJarIdentity(new MavenArtifactCoordinate(parts[1], parts[2], parts[3]));
    }
    if (parts.length == 6 && parts[0].equals("maven") && parts[4].equals("jar")) {
      return new MavenJarIdentity(
          new MavenArtifactCoordinate(parts[1], parts[2], parts[3]), parts[5]);
    }
    throw new IllegalArgumentException("invalid bundled JAR identity " + canonical);
  }

  private static ResolvedJarArtifact required(
      Map<String, ResolvedJarArtifact> artifacts, String identity) {
    ResolvedJarArtifact result = artifacts.get(identity);
    if (result == null) throw new IllegalArgumentException("unknown bundled JAR " + identity);
    return result;
  }

  private static Path normalize(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }
}
