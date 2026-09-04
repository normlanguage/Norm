package dev.w0fv1.norm.project;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleArchiveFormat;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipFile;

final class ModuleArchiveReader {
  ArchivedModule read(Path archive) throws IOException {
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      var manifestEntry = zip.getEntry("module.json");
      if (manifestEntry == null) throw new IOException("module archive has no manifest");
      JsonObject manifest;
      try (var input = zip.getInputStream(manifestEntry)) {
        manifest =
            JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
      }
      int formatVersion = manifest.get("formatVersion").getAsInt();
      if (!ModuleArchiveFormat.isReadable(formatVersion)) {
        throw new IOException("unsupported module archive format");
      }
      ModuleDescriptor descriptor = descriptor(manifest, formatVersion);
      Map<String, String> sources = new LinkedHashMap<>();
      Map<String, ModuleResource> resources = new LinkedHashMap<>();
      var entries = zip.entries();
      String prefix = "sources/";
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
        String relativePath = entry.getName().substring(prefix.length());
        try (var input = zip.getInputStream(entry)) {
          if (sources.putIfAbsent(
                  relativePath, new String(input.readAllBytes(), StandardCharsets.UTF_8))
              != null) {
            throw new IOException("duplicate module source " + relativePath);
          }
        }
      }
      entries = zip.entries();
      prefix = "resources/";
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
        String relativePath = entry.getName().substring(prefix.length());
        try (var input = zip.getInputStream(entry)) {
          ModuleResource resource = new ModuleResource(relativePath, input.readAllBytes());
          if (resources.putIfAbsent(relativePath, resource) != null) {
            throw new IOException("duplicate module resource " + relativePath);
          }
        }
      }
      return new ArchivedModule(
          descriptor,
          manifest.has("jar")
              ? Optional.of(
                  Sha256Digest.parse(manifest.getAsJsonObject("jar").get("apiId").getAsString()))
              : Optional.empty(),
          sources,
          resources);
    } catch (RuntimeException exception) {
      throw new IOException("invalid module archive " + archive, exception);
    }
  }

  private static ModuleDescriptor descriptor(JsonObject manifest, int formatVersion) {
    JsonObject module = manifest.getAsJsonObject("module");
    List<String> exports = new ArrayList<>();
    module.getAsJsonArray("exports").forEach(value -> exports.add(value.getAsString()));
    List<ModuleRequirement> dependencies = new ArrayList<>();
    module
        .getAsJsonArray("dependencies")
        .forEach(
            value -> {
              JsonObject dependency = value.getAsJsonObject();
              dependencies.add(
                  new ModuleRequirement(
                      repository(dependency, formatVersion),
                      dependency.get("name").getAsString(),
                      dependency.get("version").getAsInt(),
                      dependency.has("exported") && dependency.get("exported").getAsBoolean()));
            });
    Optional<JarBinding> binding = Optional.empty();
    if (manifest.has("jar")) {
      JsonObject jar = manifest.getAsJsonObject("jar");
      var target =
          new MavenJarTarget(
              new MavenArtifactCoordinate(
                  jar.get("group").getAsString(),
                  jar.get("artifact").getAsString(),
                  jar.get("version").getAsString()),
              Optional.of(Sha256Digest.parse(jar.get("resolution").getAsString())));
      List<JarBindingType> api = new ArrayList<>();
      jar.getAsJsonArray("api")
          .forEach(
              value -> {
                JsonObject type = value.getAsJsonObject();
                List<String> members = new ArrayList<>();
                type.getAsJsonArray("members").forEach(member -> members.add(member.getAsString()));
                List<JarBindingOverload> overloads = new ArrayList<>();
                type.getAsJsonArray("overloads")
                    .forEach(
                        overloadValue -> {
                          JsonObject overload = overloadValue.getAsJsonObject();
                          List<String> parameterTypes = new ArrayList<>();
                          overload
                              .getAsJsonArray("parameterTypes")
                              .forEach(parameter -> parameterTypes.add(parameter.getAsString()));
                          overloads.add(
                              new JarBindingOverload(
                                  overload.get("name").getAsString(), parameterTypes));
                        });
                api.add(new JarBindingType(type.get("name").getAsString(), members, overloads));
              });
      binding = Optional.of(new JarBinding(target, api));
    }
    int version = module.get("version").getAsInt();
    if (version < 1)
      throw new IllegalArgumentException("published module version must be positive");
    return new ModuleDescriptor(
        new ModuleCoordinate(module.get("name").getAsString(), version),
        exports,
        dependencies,
        binding);
  }

  private static String repository(JsonObject dependency, int formatVersion) {
    if (dependency.has("repository")) return dependency.get("repository").getAsString();
    if (formatVersion == 4) return "github";
    throw new IllegalArgumentException("module dependency has no repository");
  }

  record ArchivedModule(
      ModuleDescriptor descriptor,
      Optional<Sha256Digest> javaApiId,
      Map<String, String> sources,
      Map<String, ModuleResource> resources) {
    ArchivedModule {
      java.util.Objects.requireNonNull(descriptor, "descriptor");
      java.util.Objects.requireNonNull(javaApiId, "javaApiId");
      if (descriptor.binding().isPresent() != javaApiId.isPresent()) {
        throw new IllegalArgumentException("module JAR binding identity is incomplete");
      }
      sources = Map.copyOf(sources);
      resources = Map.copyOf(resources);
    }
  }
}
