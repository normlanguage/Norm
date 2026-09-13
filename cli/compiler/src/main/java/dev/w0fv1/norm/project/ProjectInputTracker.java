package dev.w0fv1.norm.project;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.DirectorySnapshot;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

final class ProjectInputTracker {
  private final ArrayList<FileSnapshot> files = new ArrayList<>();
  private final ArrayList<DirectorySnapshot> directories = new ArrayList<>();
  private final ArrayList<Path> absent = new ArrayList<>();

  void clear() {
    files.clear();
    directories.clear();
    absent.clear();
  }

  void source(SourceFile source) {
    files.add(
        new FileSnapshot(
            source.path(), Sha256Digest.compute(source.text().getBytes(StandardCharsets.UTF_8))));
  }

  void candidate(Path path) throws IOException {
    if (Files.isRegularFile(path)) files.add(FileSnapshot.capture(path));
    else absent.add(path.toAbsolutePath().normalize());
  }

  void directory(Path path, boolean normSourcesOnly) throws IOException {
    directories.add(DirectorySnapshot.capture(path, normSourcesOnly));
  }

  ProjectInputSnapshot snapshot(ProjectSourceSet sources) {
    var inputs = new ArrayList<>(files);
    inputs.addAll(sources.moduleArchives().values());
    for (var binding : sources.jarBindings()) {
      for (var artifact : binding.graph().artifacts())
        inputs.add(new FileSnapshot(artifact.file(), artifact.content()));
    }
    return new ProjectInputSnapshot(inputs, directories, absent);
  }
}
