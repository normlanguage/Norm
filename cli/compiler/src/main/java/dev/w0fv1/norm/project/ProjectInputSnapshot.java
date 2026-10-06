package dev.w0fv1.norm.project;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.DirectorySnapshot;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.InputWatch;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ProjectInputSnapshot(
    List<FileSnapshot> files, List<DirectorySnapshot> directories, List<Path> absent) {
  public ProjectInputSnapshot {
    files = files.stream().distinct().toList();
    directories = directories.stream().distinct().toList();
    absent = absent.stream().map(path -> path.toAbsolutePath().normalize()).distinct().toList();
  }

  public static ProjectInputSnapshot empty() {
    return new ProjectInputSnapshot(List.of(), List.of(), List.of());
  }

  public static ProjectInputSnapshot sources(Collection<SourceFile> sources) {
    return new ProjectInputSnapshot(
        sources.stream()
            .map(
                source ->
                    new FileSnapshot(
                        source.path(),
                        Sha256Digest.compute(source.text().getBytes(StandardCharsets.UTF_8))))
            .toList(),
        List.of(),
        List.of());
  }

  public boolean affects(Path path) {
    Path changed = path.toAbsolutePath().normalize();
    if (files.stream().anyMatch(file -> file.path().startsWith(changed))
        || absent.stream().anyMatch(candidate -> candidate.startsWith(changed))) return true;
    return directories.stream()
        .anyMatch(
            directory ->
                directory.root().startsWith(changed)
                    || directory.files().stream().anyMatch(file -> file.path().startsWith(changed))
                    || changed.startsWith(directory.root())
                        && (!directory.normSourcesOnly()
                            || changed.getFileName().toString().endsWith(".norm")));
  }

  public List<InputWatch> watches() {
    Set<InputWatch> watches = new LinkedHashSet<>();
    directories.forEach(
        directory ->
            watches.add(
                new InputWatch(
                    directory.root(), directory.normSourcesOnly() ? "**/*.norm" : "**/*")));
    var paths = new LinkedHashSet<Path>();
    files.forEach(file -> paths.add(file.path()));
    paths.addAll(absent);
    paths.forEach(
        path -> {
          if (path.getParent() != null) watches.add(InputWatch.file(path));
        });
    return List.copyOf(watches);
  }

  public boolean matches() {
    try {
      for (Path path : absent) if (Files.exists(path)) return false;
      for (FileSnapshot file : files) file.verify();
      for (DirectorySnapshot directory : directories) if (!directory.matches()) return false;
      return true;
    } catch (IOException missingOrChanged) {
      return false;
    }
  }
}
