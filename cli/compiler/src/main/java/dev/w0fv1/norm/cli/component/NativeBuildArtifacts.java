package dev.w0fv1.norm.cli.component;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record NativeBuildArtifacts(Path root, Path image, List<Path> files) {
  NativeBuildArtifacts {
    files = List.copyOf(files);
  }

  static NativeBuildArtifacts read(Path staging, Path executable) throws IOException {
    Path root = staging.toRealPath();
    Path image = executable.toRealPath();
    Set<Path> files = new LinkedHashSet<>();
    try (var reader = Files.newBufferedReader(root.resolve("build-artifacts.json"))) {
      var manifest = JsonParser.parseReader(reader).getAsJsonObject();
      for (var entry : manifest.entrySet()) {
        boolean runtime =
            switch (entry.getKey()) {
              case "executables",
                  "image_layer",
                  "image_layer_bundle",
                  "shared_libraries",
                  "jdk_libraries",
                  "language_home",
                  "language_resources" ->
                  true;
              case "build_info", "debug_info", "c_headers", "import_libraries" -> false;
              default ->
                  throw new IOException("Unknown Native Image artifact kind: " + entry.getKey());
            };
        if (!runtime) continue;
        for (var value : entry.getValue().getAsJsonArray()) {
          Path path = root.resolve(value.getAsString()).normalize();
          if (!path.startsWith(root) || !path.toRealPath().startsWith(root))
            throw new IOException("Native runtime artifact escapes build directory: " + path);
          try (var contents = Files.walk(path, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
            for (Path file : contents.filter(Files::isRegularFile).toList()) {
              if (!file.toRealPath().startsWith(root))
                throw new IOException("Native runtime artifact escapes build directory: " + file);
              files.add(file);
            }
          }
        }
      }
    } catch (RuntimeException failure) {
      throw new IOException("Invalid Native Image build-artifacts.json", failure);
    }
    if (!files.contains(image))
      throw new IOException("Native runtime manifest omits executable: " + image);
    return new NativeBuildArtifacts(root, image, List.copyOf(files));
  }

  List<Path> publishLibraries(Path executable) throws IOException {
    Path output = executable.toAbsolutePath().normalize();
    Path parent = output.getParent();
    var targets = new LinkedHashMap<Path, Path>();
    for (Path file : files) {
      Path target = file.equals(image) ? output : parent.resolve(root.relativize(file)).normalize();
      if (!target.startsWith(parent) || targets.containsValue(target))
        throw new IOException("Invalid Native runtime artifact destination: " + target);
      targets.put(file, target);
      if (!file.equals(image)
          && Files.exists(target)
          && (!Files.isRegularFile(target) || Files.mismatch(file, target) != -1)) {
        throw new IOException(
            "Native runtime library conflicts with existing file: "
                + target
                + "; build into a separate directory");
      }
    }
    for (var entry : targets.entrySet()) {
      if (entry.getKey().equals(image) || Files.exists(entry.getValue())) continue;
      Files.createDirectories(entry.getValue().getParent());
      Files.copy(entry.getKey(), entry.getValue());
    }
    return List.copyOf(targets.values());
  }
}
