package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.core.store.DirectoryArtifactCache;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record PreparedApplicationContent(
    ApplicationProgramData program, Map<String, byte[]> classes, List<FileSnapshot> dependencies) {
  public PreparedApplicationContent {
    classes = Map.copyOf(classes);
    dependencies = List.copyOf(dependencies);
  }

  public Path materialize(Path directory) throws IOException {
    var application = prepare(directory);
    Path entry = directory.resolve(PreparedApplication.ENTRY);
    PortableObjectCodec.write(application, entry);
    return entry;
  }

  public PreparedApplication application() {
    var paths = new ArrayList<String>();
    paths.add("classes");
    paths.addAll(dependencyFiles().keySet());
    return new PreparedApplication(program, paths);
  }

  public DirectoryArtifactCache.Lease acquire(DirectoryArtifactCache cache) throws IOException {
    var files = new TreeMap<String, Sha256Digest>();
    classes.forEach((name, bytes) -> files.put("classes/" + name, Sha256Digest.compute(bytes)));
    dependencyFiles().forEach((name, snapshot) -> files.put(name, snapshot.content()));
    var identity = new CanonicalWriter().writeTag("application-assets-1").writeInt(files.size());
    files.forEach((name, digest) -> identity.writeString(name).writeString(digest.value()));
    return cache.acquire(
        Sha256Digest.compute(identity.toByteArray()),
        root -> {
          try (var existing = Files.walk(root)) {
            if (existing.filter(Files::isRegularFile).count() != files.size()) return false;
          }
          for (var entry : files.entrySet())
            new FileSnapshot(root.resolve(entry.getKey()), entry.getValue()).verify();
          return true;
        },
        this::prepare);
  }

  public PreparedApplication prepare(Path directory) throws IOException {
    Path root = directory.toAbsolutePath().normalize();
    Path classRoot = Files.createDirectories(root.resolve("classes"));
    for (var entry : classes.entrySet()) {
      Path target = classRoot.resolve(entry.getKey()).normalize();
      if (!target.startsWith(classRoot))
        throw new IOException("prepared resource is outside its directory");
      Files.createDirectories(target.getParent());
      Files.write(target, entry.getValue());
    }
    for (var entry : dependencyFiles().entrySet())
      entry.getValue().copyTo(root.resolve(entry.getKey()));
    return application();
  }

  private Map<String, FileSnapshot> dependencyFiles() {
    var files = new LinkedHashMap<String, FileSnapshot>();
    for (var dependency : dependencies)
      files.put("jars/" + (files.size() + 1) + "-" + dependency.path().getFileName(), dependency);
    return files;
  }
}
