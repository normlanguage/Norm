package dev.w0fv1.norm.gradle;

import dev.w0fv1.norm.packaging.ReachabilityMetadataArchive;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class PrepareReachability extends DefaultTask {
  @InputFile
  @Optional
  public abstract RegularFileProperty getLocalArchive();

  @Input
  public abstract Property<Boolean> getOffline();

  @OutputFile
  public abstract RegularFileProperty getDestination();

  @TaskAction
  public void prepare() throws IOException {
    ReachabilityMetadataArchive.OFFICIAL.prepare(
        getLocalArchive().isPresent() ? getLocalArchive().get().getAsFile().toPath() : null,
        getOffline().get(),
        getDestination().get().getAsFile().toPath());
  }
}
