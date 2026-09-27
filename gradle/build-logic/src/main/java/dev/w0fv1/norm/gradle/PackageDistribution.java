package dev.w0fv1.norm.gradle;

import dev.w0fv1.norm.packaging.RuntimePayloadArchive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

public abstract class PackageDistribution extends DefaultTask {
  @InputDirectory
  public abstract DirectoryProperty getRuntimeDirectory();

  @Input
  public abstract Property<String> getNormVersion();

  @OutputFile
  public abstract RegularFileProperty getAssetFile();

  @InputFile
  public abstract RegularFileProperty getLauncherProject();

  @InputDirectory
  public abstract DirectoryProperty getLauncherSourceDirectory();

  @InputFile
  public abstract RegularFileProperty getLauncherIcon();

  @LocalState
  public abstract DirectoryProperty getLauncherWorkDirectory();

  @LocalState
  public abstract DirectoryProperty getDotnetArtifactsDirectory();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @TaskAction
  public void packageAsset() throws IOException {
    Path runtime = getRuntimeDirectory().get().getAsFile().toPath();
    Path work = getLauncherWorkDirectory().get().getAsFile().toPath();
    Path payload = work.resolve("norm-runtime.zip");
    Path digest = work.resolve("norm-runtime.sha256");
    RuntimePayloadArchive.write(runtime, payload, digest);

    Path published = work.resolve("publish");
    Path artifacts = getDotnetArtifactsDirectory().get().getAsFile().toPath();
    getExecOperations()
        .exec(
            spec -> {
              spec.executable("dotnet");
              spec.args(
                  "publish",
                  getLauncherProject().get().getAsFile().getAbsolutePath(),
                  "--configuration",
                  "Release",
                  "--runtime",
                  "win-x64",
                  "--self-contained",
                  "true",
                  "--artifacts-path",
                  artifacts.toAbsolutePath().toString(),
                  "--output",
                  published.toAbsolutePath().toString(),
                  "-p:NormVersion=" + getNormVersion().get(),
                  "-p:NormPayloadPath=" + payload.toAbsolutePath(),
                  "-p:NormPayloadDigestPath=" + digest.toAbsolutePath());
            })
        .assertNormalExitValue();

    Path executable = published.resolve("norm.exe");
    if (!Files.isRegularFile(executable))
      throw new IOException("Launcher publish did not produce " + executable);
    Path destination = getAssetFile().get().getAsFile().toPath();
    Files.createDirectories(destination.getParent());
    Path staged = Files.createTempFile(destination.getParent(), "norm-standalone-", ".exe");
    try {
      Files.copy(executable, staged, StandardCopyOption.REPLACE_EXISTING);
      Files.move(
          staged, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(staged);
    }
  }
}
