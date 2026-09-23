package dev.w0fv1.norm.maven;

import dev.w0fv1.norm.packaging.ReachabilityMetadataArchive;
import java.io.File;
import java.io.IOException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(
    name = "reachability-metadata",
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    threadSafe = true)
public final class ReachabilityMetadataMojo extends AbstractMojo {
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Parameter(property = "norm.reachability.archive")
  private File archive;

  @Parameter(
      defaultValue =
          "${project.build.directory}/generated-resources/reachability-metadata/graalvm-reachability-metadata.zip")
  private File output;

  @Override
  public void execute() throws MojoExecutionException {
    try {
      ReachabilityMetadataArchive.OFFICIAL.prepare(
          archive == null ? null : archive.toPath(), session.isOffline(), output.toPath());
    } catch (IOException exception) {
      throw new MojoExecutionException("Cannot prepare GraalVM reachability metadata", exception);
    }
  }
}
