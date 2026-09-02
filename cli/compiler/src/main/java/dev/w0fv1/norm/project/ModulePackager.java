package dev.w0fv1.norm.project;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.w0fv1.norm.jvm.GeneratedBindingSource;
import dev.w0fv1.norm.jvm.JavaApiReportWriter;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleArchiveFormat;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleRepositoryCoordinate;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ModulePackager {
  private static final Gson JSON = new Gson();
  private final ProjectLoader projects;

  public ModulePackager(ProjectLoader projects) {
    this.projects = Objects.requireNonNull(projects, "projects");
  }

  public PackagedModule packageModule(Path modulePath, Path repository) throws IOException {
    SourceFile moduleSource = SourceFile.read(modulePath.toAbsolutePath().normalize());
    ProjectLoader.ModuleArchiveContents contents = projects.moduleArchiveContents(moduleSource);
    ModuleDescriptor descriptor = contents.descriptor();
    Optional<MavenJarTarget> target = Optional.empty();
    if (descriptor.binding().isPresent()) {
      if (!(descriptor.binding().orElseThrow().target() instanceof MavenJarTarget maven)) {
        throw new IOException("publishable JAR binding modules require a Maven root artifact");
      }
      if (maven.resolution().isEmpty()) {
        throw new IOException("JAR binding must be pinned with 'norm resolve' before packaging");
      }
      target = Optional.of(maven);
    }
    ModuleRepositoryCoordinate coordinate =
        ModuleRepositoryCoordinate.from(descriptor.coordinate());
    Path versionDirectory =
        repository
            .toAbsolutePath()
            .normalize()
            .resolve(coordinate.group().replace('.', java.io.File.separatorChar))
            .resolve(coordinate.artifact())
            .resolve(coordinate.version());
    Files.createDirectories(versionDirectory);
    String fileName = coordinate.artifact() + "-" + coordinate.version();
    Path archive = versionDirectory.resolve(fileName + ModuleArchiveFormat.FILE_SUFFIX);
    Path pom = versionDirectory.resolve(fileName + ".pom");
    Map<String, SourceFile> archiveSources = new LinkedHashMap<>();
    if (contents.binding().isPresent()) {
      for (GeneratedBindingSource generated :
          contents.binding().orElseThrow().generated().sources()) {
        addSource(archiveSources, contents.sources(), generated.relativePath());
      }
      int bindingExports = descriptor.binding().orElseThrow().api().size();
      for (String export :
          descriptor.exports().subList(bindingExports, descriptor.exports().size())) {
        addSource(archiveSources, contents.sources(), descriptor.sourcePath(export));
      }
    } else {
      archiveSources.putAll(contents.sources());
    }
    writeArchive(archive, descriptor, archiveSources, contents.binding(), contents.resources());
    Files.writeString(pom, pom(descriptor, coordinate, target), StandardCharsets.UTF_8);
    return new PackagedModule(archive.toAbsolutePath(), pom.toAbsolutePath());
  }

  private static void addSource(
      Map<String, SourceFile> destination, Map<String, SourceFile> sources, String path)
      throws IOException {
    SourceFile source = sources.get(path);
    if (source == null) throw new IOException("module source is absent: " + path);
    destination.put(path, source);
  }

  private static void writeArchive(
      Path path,
      ModuleDescriptor descriptor,
      Map<String, SourceFile> sources,
      Optional<ResolvedJarBinding> binding,
      Map<String, ModuleResource> resources)
      throws IOException {
    try (OutputStream file = Files.newOutputStream(path);
        ZipOutputStream archive = new ZipOutputStream(file)) {
      writeEntry(archive, "module.json", manifest(descriptor, binding));
      if (binding.isPresent()) {
        writeEntry(
            archive,
            "binding/java-api.json",
            writer -> new JavaApiReportWriter().write(binding.orElseThrow().api(), writer));
      }
      for (Map.Entry<String, SourceFile> source :
          sources.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        writeEntry(archive, "sources/" + source.getKey(), source.getValue().text());
      }
      for (ModuleResource resource :
          resources.values().stream()
              .sorted(java.util.Comparator.comparing(ModuleResource::path))
              .toList()) {
        writeEntry(archive, "resources/" + resource.path(), resource.content());
      }
    }
  }

  private static String manifest(
      ModuleDescriptor descriptor, Optional<ResolvedJarBinding> binding) {
    JsonObject root = new JsonObject();
    root.addProperty("formatVersion", ModuleArchiveFormat.FORMAT_VERSION);
    JsonObject module = new JsonObject();
    module.addProperty("name", descriptor.name());
    module.addProperty("version", descriptor.version());
    JsonArray exports = new JsonArray();
    descriptor.exports().forEach(exports::add);
    module.add("exports", exports);
    JsonArray dependencies = new JsonArray();
    for (ModuleRequirement dependency : descriptor.dependencies()) {
      JsonObject value = new JsonObject();
      value.addProperty("name", dependency.name());
      value.addProperty("version", dependency.version());
      dependencies.add(value);
    }
    module.add("dependencies", dependencies);
    root.add("module", module);
    if (binding.isPresent()) {
      MavenJarTarget target = (MavenJarTarget) descriptor.binding().orElseThrow().target();
      JsonObject jar = new JsonObject();
      jar.addProperty("group", target.coordinate().group());
      jar.addProperty("artifact", target.coordinate().artifact());
      jar.addProperty("version", target.coordinate().version());
      jar.addProperty("resolution", target.resolution().orElseThrow().value());
      jar.addProperty("apiId", binding.orElseThrow().api().apiId().value());
      JsonArray api = new JsonArray();
      descriptor
          .binding()
          .orElseThrow()
          .api()
          .forEach(
              type -> {
                JsonObject value = new JsonObject();
                value.addProperty("name", type.name());
                JsonArray members = new JsonArray();
                type.members().forEach(members::add);
                value.add("members", members);
                JsonArray overloads = new JsonArray();
                type.overloads()
                    .forEach(
                        overload -> {
                          JsonObject overloadValue = new JsonObject();
                          overloadValue.addProperty("name", overload.name());
                          JsonArray parameterTypes = new JsonArray();
                          overload.parameterTypes().forEach(parameterTypes::add);
                          overloadValue.add("parameterTypes", parameterTypes);
                          overloads.add(overloadValue);
                        });
                value.add("overloads", overloads);
                api.add(value);
              });
      jar.add("api", api);
      root.add("jar", jar);
    }
    return JSON.toJson(root) + "\n";
  }

  private static String pom(
      ModuleDescriptor descriptor,
      ModuleRepositoryCoordinate coordinate,
      Optional<MavenJarTarget> target) {
    StringBuilder dependencies = new StringBuilder();
    target.ifPresent(
        value ->
            appendDependency(
                dependencies,
                value.coordinate().group(),
                value.coordinate().artifact(),
                value.coordinate().version(),
                DependencyType.JAR));
    for (ModuleRequirement dependency : descriptor.dependencies()) {
      ModuleRepositoryCoordinate dependencyCoordinate =
          ModuleRepositoryCoordinate.from(dependency.coordinate());
      appendDependency(
          dependencies,
          dependencyCoordinate.group(),
          dependencyCoordinate.artifact(),
          dependencyCoordinate.version(),
          DependencyType.NAR);
    }
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>%s</version>
          <packaging>%s</packaging>
          <dependencies>
        %s  </dependencies>
        </project>
        """
        .formatted(
            xml(coordinate.group()),
            xml(coordinate.artifact()),
            coordinate.version(),
            ModuleArchiveFormat.EXTENSION,
            dependencies);
  }

  private static void appendDependency(
      StringBuilder output, String group, String artifact, String version, DependencyType type) {
    output
        .append("    <dependency>\n")
        .append("      <groupId>")
        .append(xml(group))
        .append("</groupId>\n")
        .append("      <artifactId>")
        .append(xml(artifact))
        .append("</artifactId>\n")
        .append("      <version>")
        .append(xml(version))
        .append("</version>\n");
    if (type == DependencyType.NAR) {
      output.append("      <type>").append(ModuleArchiveFormat.EXTENSION).append("</type>\n");
    }
    output.append("      <scope>runtime</scope>\n").append("    </dependency>\n");
  }

  private static String xml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private static void writeEntry(ZipOutputStream output, String name, String content)
      throws IOException {
    writeEntry(output, name, writer -> writer.write(content));
  }

  private static void writeEntry(ZipOutputStream output, String name, byte[] content)
      throws IOException {
    ZipEntry entry = new ZipEntry(name);
    entry.setTime(0);
    output.putNextEntry(entry);
    output.write(content);
    output.closeEntry();
  }

  private static void writeEntry(ZipOutputStream output, String name, EntryWriter content)
      throws IOException {
    ZipEntry entry = new ZipEntry(name);
    entry.setTime(0);
    output.putNextEntry(entry);
    Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
    content.write(writer);
    writer.flush();
    output.closeEntry();
  }

  @FunctionalInterface
  private interface EntryWriter {
    void write(Writer writer) throws IOException;
  }

  private enum DependencyType {
    JAR,
    NAR
  }

  public record PackagedModule(Path archive, Path pom) {
    public PackagedModule {
      archive = Objects.requireNonNull(archive, "archive").toAbsolutePath().normalize();
      pom = Objects.requireNonNull(pom, "pom").toAbsolutePath().normalize();
    }
  }
}
