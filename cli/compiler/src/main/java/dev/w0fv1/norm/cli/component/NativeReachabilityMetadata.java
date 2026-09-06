package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.jvm.JarBindingClasspath;
import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.graalvm.reachability.DirectoryConfiguration;
import org.graalvm.reachability.internal.FileSystemRepository;

final class NativeReachabilityMetadata {
  private static final String REPOSITORY_RESOURCE = "/graalvm-reachability-metadata.zip";

  Result prepare(JarBindingClasspath classpathPlan, Path destination) throws IOException {
    Set<String> coordinates = coordinates(classpathPlan);
    Path repositoryRoot = destination.resolve("repository");
    Set<DirectoryConfiguration> configurations = Set.of();
    if (!coordinates.isEmpty()) {
      extract(repositoryRoot);
      var repository = new FileSystemRepository(repositoryRoot);
      configurations =
          repository.findConfigurationsFor(
              query -> {
                query.forArtifacts(coordinates);
                query.useLatestConfigWhenVersionIsUntested();
              });
    }
    Path classpath = destination.resolve("classpath");
    Files.createDirectories(classpath);
    DirectoryConfiguration.copy(configurations, classpath);
    var sources = new com.google.gson.JsonArray();
    for (var configuration :
        configurations.stream()
            .sorted(
                java.util.Comparator.comparing(DirectoryConfiguration::getGroupId)
                    .thenComparing(DirectoryConfiguration::getArtifactId)
                    .thenComparing(DirectoryConfiguration::getVersion))
            .toList()) {
      var source = new com.google.gson.JsonObject();
      var origin = configuration.getDirectory();
      Path moduleRoot =
          repositoryRoot.resolve(configuration.getGroupId()).resolve(configuration.getArtifactId());
      String metadataVersion = moduleRoot.relativize(origin).toString().replace('\\', '/');
      source.addProperty(
          "artifact",
          configuration.getGroupId()
              + ":"
              + configuration.getArtifactId()
              + ":"
              + configuration.getVersion());
      source.addProperty("metadataVersion", metadataVersion);
      source.addProperty("override", configuration.isOverride());
      Path index = moduleRoot.resolve("index.json");
      var declarations =
          com.google.gson.JsonParser.parseString(Files.readString(index)).getAsJsonArray();
      boolean tested =
          declarations.asList().stream()
              .map(com.google.gson.JsonElement::getAsJsonObject)
              .filter(
                  value ->
                      value.has("metadata-version")
                          && value.get("metadata-version").getAsString().equals(metadataVersion))
              .filter(value -> value.has("tested-versions"))
              .flatMap(value -> value.getAsJsonArray("tested-versions").asList().stream())
              .anyMatch(version -> version.getAsString().equals(configuration.getVersion()));
      source.addProperty("versionTested", tested);
      source.addProperty("indexSha256", dev.w0fv1.norm.value.Sha256Digest.compute(index).value());
      var files = new com.google.gson.JsonObject();
      source.addProperty("sourceDirectoryPresent", Files.isDirectory(origin));
      if (Files.isDirectory(origin)) {
        try (var paths = Files.walk(origin)) {
          for (var file : paths.filter(Files::isRegularFile).sorted().toList()) {
            files.addProperty(
                origin.relativize(file).toString().replace('\\', '/'),
                dev.w0fv1.norm.value.Sha256Digest.compute(file).value());
          }
        }
      }
      source.add("files", files);
      sources.add(source);
    }
    var manifest = new com.google.gson.JsonObject();
    manifest.addProperty("schemaVersion", 1);
    manifest.add("configurations", sources);
    Path manifestPath = destination.resolve("reachability-sources.json");
    Files.writeString(
        manifestPath,
        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n");
    return new Result(classpath, configurations.size(), manifestPath);
  }

  static Set<String> coordinates(List<ResolvedJarBinding> bindings) {
    return coordinates(JarBindingClasspath.prepare(bindings));
  }

  static Set<String> coordinates(JarBindingClasspath classpathPlan) {
    Set<String> result = new LinkedHashSet<>();
    classpathPlan.artifacts().stream()
        .map(artifact -> artifact.identity())
        .filter(MavenJarIdentity.class::isInstance)
        .map(MavenJarIdentity.class::cast)
        .map(identity -> identity.coordinate().notation())
        .forEach(result::add);
    return Collections.unmodifiableSet(result);
  }

  private static void extract(Path destination) throws IOException {
    Files.createDirectories(destination);
    InputStream resource =
        NativeReachabilityMetadata.class.getResourceAsStream(REPOSITORY_RESOURCE);
    if (resource == null) {
      throw new IOException("GraalVM reachability metadata is missing from the Norm runtime");
    }
    try (resource;
        ZipInputStream archive = new ZipInputStream(resource)) {
      ZipEntry entry;
      while ((entry = archive.getNextEntry()) != null) {
        Path target = destination.resolve(entry.getName()).normalize();
        if (!target.startsWith(destination)) {
          throw new IOException("invalid GraalVM reachability metadata archive entry");
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(archive, target);
        }
        archive.closeEntry();
      }
    }
  }

  record Result(Path classpath, int configurationCount, Path manifest) {}
}
