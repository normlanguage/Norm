package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.core.store.CompilerArtifactIdentity;
import dev.w0fv1.norm.core.store.FileArtifactCache;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JavaCompilationCache {
  private final FileArtifactCache artifacts;

  JavaCompilationCache(Path directory) throws IOException {
    artifacts = new FileArtifactCache(directory, 128, 256L * 1024 * 1024);
  }

  static Sha256Digest key(List<JavaAnnotationStub> stubs, List<Path> classpath, Path javac)
      throws IOException {
    var writer =
        new CanonicalWriter()
            .writeTag("javac-classes-1")
            .writeString(CompilerArtifactIdentity.current())
            .writeInt(stubs.size());
    for (var stub : stubs) writer.writeString(stub.binaryName()).writeString(stub.source());
    writer.writeInt(classpath.size());
    for (Path entry : classpath) content(writer, entry);
    for (Path entry : toolchainFiles(javac)) content(writer, entry);
    return Sha256Digest.compute(writer.toByteArray());
  }

  static List<Path> toolchainFiles(Path javac) {
    Path home = javac.toAbsolutePath().normalize().getParent().getParent();
    return List.of(
        javac, home.resolve("release"), home.resolve("lib/modules"), home.resolve("lib/ct.sym"));
  }

  boolean restore(Sha256Digest key, Path classes) throws IOException {
    var stored = artifacts.read(key);
    if (stored.isEmpty()) return false;
    var files = PortableObjectCodec.decode(stored.orElseThrow(), Classes.class).files();
    Path root = classes.toAbsolutePath().normalize();
    for (var entry : files.entrySet()) {
      Path target = root.resolve(entry.getKey()).normalize();
      if (!target.startsWith(root) || !entry.getKey().endsWith(".class"))
        throw new IOException("invalid cached Java class path");
      Files.createDirectories(target.getParent());
      Files.write(target, entry.getValue());
    }
    return true;
  }

  void write(Sha256Digest key, Path classes) throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();
    try (var paths = Files.walk(classes)) {
      for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
        files.put(classes.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
      }
    }
    artifacts.write(key, PortableObjectCodec.encode(new Classes(files)));
  }

  private static void content(CanonicalWriter writer, Path path) throws IOException {
    writer.writeString(path.getFileName().toString()).writeBoolean(Files.exists(path));
    if (Files.isDirectory(path)) {
      try (var files = Files.walk(path)) {
        var entries = files.filter(Files::isRegularFile).sorted().toList();
        writer.writeInt(entries.size());
        for (var file : entries)
          writer
              .writeString(path.relativize(file).toString())
              .writeString(Sha256Digest.compute(file).value());
      }
    } else if (Files.isRegularFile(path)) {
      writer.writeString(Sha256Digest.compute(path).value());
    }
  }

  private record Classes(Map<String, byte[]> files) {
    private Classes {
      files = Map.copyOf(files);
    }
  }
}
