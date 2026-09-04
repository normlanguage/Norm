package dev.w0fv1.norm.project;

import com.google.gson.JsonObject;
import dev.w0fv1.norm.frontend.SourceStructure;
import dev.w0fv1.norm.jvm.BundledJarGraphs;
import dev.w0fv1.norm.value.ModuleArchiveFormat;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleRepositoryCoordinate;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.Sha256Digest;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ApplicationBundleWriter {
  public static final String DESCRIPTOR = "application.json";

  public Path write(ProjectSourceSet sourceSet, Path destination) throws IOException {
    Objects.requireNonNull(sourceSet, "sourceSet");
    Path output = normalize(destination);
    Path parent = output.getParent();
    if (parent == null) throw new IOException("application bundle has no parent directory");
    Files.createDirectories(parent);
    Path staging = Files.createTempDirectory(parent, ".norm-application-");
    try {
      Path sourceRoot = staging.resolve("source");
      Files.createDirectories(sourceRoot);
      Path entry = writeSources(sourceSet, sourceRoot);
      writePackages(sourceSet, staging.resolve("packages"));
      BundledJarGraphs.write(staging.resolve("jars"), sourceSet.jarBindings());
      JsonObject descriptor = new JsonObject();
      descriptor.addProperty("formatVersion", 1);
      descriptor.addProperty("entry", unixPath(staging.relativize(entry)));
      Files.writeString(staging.resolve(DESCRIPTOR), descriptor.toString(), StandardCharsets.UTF_8);
      Path temporary = Files.createTempFile(parent, output.getFileName().toString(), ".part");
      try {
        zip(staging, temporary);
        move(temporary, output);
      } finally {
        Files.deleteIfExists(temporary);
      }
      return output;
    } finally {
      deleteTree(staging);
    }
  }

  private static Path writeSources(ProjectSourceSet sourceSet, Path destination)
      throws IOException {
    ModuleCoordinate rootCoordinate =
        sourceSet.scope().coordinate(sourceSet.primarySource().id()).module();
    ModuleDescriptor descriptor = sourceSet.moduleDescriptors().get(rootCoordinate);
    boolean singleFile =
        sourceSet.rootModulePath().isEmpty()
            || sourceSet.rootModulePath().orElseThrow().equals(sourceSet.primaryPath());
    if (singleFile) {
      Path entry = destination.resolve(sourceSet.primaryPath().getFileName());
      String source = sourceSet.primarySource().text();
      if (descriptor != null) {
        source = SourceStructure.inspect(sourceSet.primarySource()).programSource().text();
        source = source.stripTrailing() + System.lineSeparator() + moduleSource(descriptor);
      }
      Files.writeString(entry, source, StandardCharsets.UTF_8);
      return entry;
    }
    for (SourceFile source : sourceSet.sources()) {
      if (!sourceSet.scope().coordinate(source.id()).module().equals(rootCoordinate)) continue;
      Path relative = sourceSet.root().relativize(source.path());
      Path target = destination.resolve(relative).normalize();
      if (!target.startsWith(destination))
        throw new IOException("application source is outside its root");
      Files.createDirectories(target.getParent());
      Files.writeString(target, source.text(), StandardCharsets.UTF_8);
    }
    Path modulePath = sourceSet.rootModulePath().orElseThrow();
    Path bundledModule = destination.resolve(sourceSet.root().relativize(modulePath));
    Files.createDirectories(bundledModule.getParent());
    Files.writeString(bundledModule, moduleSource(descriptor), StandardCharsets.UTF_8);
    Path resources = modulePath.getParent().resolve("resources");
    if (Files.isDirectory(resources)) {
      try (var paths = Files.walk(resources)) {
        for (Path resource : paths.filter(Files::isRegularFile).sorted().toList()) {
          Path target =
              bundledModule
                  .getParent()
                  .resolve("resources")
                  .resolve(resources.relativize(resource));
          Files.createDirectories(target.getParent());
          Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
    return destination.resolve(sourceSet.root().relativize(sourceSet.primaryPath()));
  }

  private static void writePackages(ProjectSourceSet sourceSet, Path destination)
      throws IOException {
    Files.createDirectories(destination);
    for (var entry : sourceSet.moduleArchives().entrySet()) {
      ModuleRepositoryCoordinate coordinate = ModuleRepositoryCoordinate.from(entry.getKey());
      Path directory =
          destination
              .resolve(coordinate.group().replace('.', java.io.File.separatorChar))
              .resolve(coordinate.artifact())
              .resolve(coordinate.version());
      Files.createDirectories(directory);
      Path archive =
          directory.resolve(
              coordinate.artifact() + "-" + coordinate.version() + ModuleArchiveFormat.FILE_SUFFIX);
      Files.copy(entry.getValue(), archive, StandardCopyOption.REPLACE_EXISTING);
      Files.writeString(
          archive.resolveSibling(archive.getFileName() + ".sha256"),
          Sha256Digest.compute(archive).value() + System.lineSeparator(),
          StandardCharsets.UTF_8);
    }
  }

  private static String moduleSource(ModuleDescriptor descriptor) {
    StringBuilder source = new StringBuilder("Module module() {\n  return module(\n");
    if (!descriptor.coordinate().equals(ModuleCoordinate.localApplication())
        && descriptor.version() > 0) {
      source
          .append("    name: \"")
          .append(descriptor.name())
          .append("\",\n    version: ")
          .append(descriptor.version())
          .append(",\n");
    }
    if (descriptor.dependencies().isEmpty()) {
      return source.append("    dependencies: []\n  )\n}\n").toString();
    }
    source.append("    dependencies: [\n");
    for (int index = 0; index < descriptor.dependencies().size(); index++) {
      ModuleRequirement dependency = descriptor.dependencies().get(index);
      source
          .append("      ")
          .append(dependency.exported() ? "exportedDependency" : "dependency")
          .append("(repository: \"")
          .append(dependency.repository().value())
          .append("\", name: \"")
          .append(dependency.name())
          .append("\", version: ")
          .append(dependency.version())
          .append(')');
      if (index + 1 < descriptor.dependencies().size()) source.append(',');
      source.append('\n');
    }
    source.append("    ]\n  )\n}\n");
    return source.toString();
  }

  private static void zip(Path root, Path destination) throws IOException {
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
      try (var paths = Files.walk(root)) {
        for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
          ZipEntry entry = new ZipEntry(unixPath(root.relativize(path)));
          entry.setTime(0);
          output.putNextEntry(entry);
          Files.copy(path, output);
          output.closeEntry();
        }
      }
    }
  }

  private static void move(Path source, Path destination) throws IOException {
    try {
      Files.move(
          source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }

  private static String unixPath(Path path) {
    return path.toString().replace('\\', '/');
  }

  private static Path normalize(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }
}
