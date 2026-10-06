package dev.w0fv1.norm.project;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.DirectorySnapshot;
import dev.w0fv1.norm.value.FileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

final class ProjectInputTracker {
  private final LinkedHashMap<Path, FileSnapshot> files = new LinkedHashMap<>();
  private final ArrayList<DirectorySnapshot> directories = new ArrayList<>();
  private final LinkedHashSet<Path> absent = new LinkedHashSet<>();

  void resolution(dev.w0fv1.norm.packages.NormPackageResolver.ResolutionInputs inputs) {
    inputs.files().forEach(file -> files.put(file.path(), file));
    absent.addAll(inputs.absent());
  }

  void source(SourceFile source) {
    var captured = ProjectInputSnapshot.sources(List.of(source)).files().getFirst();
    files.put(captured.path(), captured);
    absent.remove(captured.path());
  }

  void candidate(Path path) throws IOException {
    if (Files.isRegularFile(path)) {
      var captured = FileSnapshot.capture(path);
      files.put(captured.path(), captured);
      absent.remove(captured.path());
    } else absent.add(path.toAbsolutePath().normalize());
  }

  void directory(Path path, boolean normSourcesOnly) throws IOException {
    directories.add(DirectorySnapshot.capture(path, normSourcesOnly));
  }

  void graph(dev.w0fv1.norm.jvm.ResolvedJarGraph graph) {
    graph
        .artifacts()
        .forEach(
            artifact ->
                files.put(artifact.file(), new FileSnapshot(artifact.file(), artifact.content())));
  }

  ProjectInputSnapshot snapshot() {
    return new ProjectInputSnapshot(List.copyOf(files.values()), directories, List.copyOf(absent));
  }
}
