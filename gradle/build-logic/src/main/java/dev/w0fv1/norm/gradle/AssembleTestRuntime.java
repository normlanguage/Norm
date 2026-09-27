package dev.w0fv1.norm.gradle;

import dev.w0fv1.norm.packaging.RuntimeModuleAssembler;
import dev.w0fv1.norm.packaging.RuntimeStorage;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class AssembleTestRuntime extends DefaultTask {
  @Classpath
  public abstract ConfigurableFileCollection getRuntimeClasspath();

  @Input
  public abstract Property<String> getStorage();

  @OutputDirectory
  public abstract DirectoryProperty getDestination();

  @TaskAction
  public void assemble() throws IOException {
    RuntimeModuleAssembler.assembleDependencies(
        getRuntimeClasspath().getFiles().stream().map(java.io.File::toPath).toList(),
        getDestination().get().getAsFile().toPath(),
        RuntimeStorage.parse(getStorage().get()));
  }
}
