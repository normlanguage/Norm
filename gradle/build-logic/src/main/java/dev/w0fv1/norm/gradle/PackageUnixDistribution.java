package dev.w0fv1.norm.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

public abstract class PackageUnixDistribution extends DefaultTask {
  @InputDirectory
  public abstract DirectoryProperty getRuntimeDirectory();

  @LocalState
  public abstract DirectoryProperty getStagingDirectory();

  @OutputFile
  public abstract RegularFileProperty getArchive();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @TaskAction
  public void packageAsset() throws IOException {
    Path staging = getStagingDirectory().get().getAsFile().toPath();
    if (Files.exists(staging)) {
      try (var paths = Files.walk(staging)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
      }
    }
    Files.createDirectories(staging.resolve("norm"));
    getExecOperations()
        .exec(
            spec -> {
              spec.executable("cp");
              spec.args(
                  "-RPp",
                  getRuntimeDirectory().get().getAsFile().toPath().resolve(".").toString(),
                  staging.resolve("norm").toString());
            })
        .assertNormalExitValue();
    Path destination = getArchive().get().getAsFile().toPath();
    Files.createDirectories(destination.getParent());
    Path temporary = Files.createTempFile(destination.getParent(), "norm-distribution-", ".tar.gz");
    try {
      getExecOperations()
          .exec(
              spec -> {
                spec.executable("tar");
                spec.args("-czf", temporary.toString(), "-C", staging.toString(), "norm");
              })
          .assertNormalExitValue();
      Files.move(
          temporary,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
