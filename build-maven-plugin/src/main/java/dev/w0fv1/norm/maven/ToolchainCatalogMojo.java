package dev.w0fv1.norm.maven;

import dev.w0fv1.norm.packaging.RuntimeModuleAssembler;
import dev.w0fv1.norm.packaging.ToolchainArtifactCatalogGenerator;
import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;

@Mojo(
    name = "toolchain-catalog",
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    requiresDependencyResolution = ResolutionScope.RUNTIME,
    threadSafe = true)
public final class ToolchainCatalogMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Component private DependencyGraphBuilder graphBuilder;

  @Parameter(required = true)
  private List<String> executionModules;

  @Parameter(required = true)
  private List<String> hostedModules;

  @Parameter(defaultValue = "${project.build.directory}/toolchain-dependencies")
  private File dependenciesDirectory;

  @Parameter(defaultValue = "${project.build.directory}/generated-resources/toolchain")
  private File resourcesDirectory;

  @Override
  public void execute() throws MojoExecutionException {
    try {
      var request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
      request.setProject(project);
      var root =
          graphBuilder.buildDependencyGraph(
              request,
              artifact ->
                  Artifact.SCOPE_COMPILE.equals(artifact.getScope())
                      || Artifact.SCOPE_RUNTIME.equals(artifact.getScope()));
      var graph = MavenToolchainGraph.from(root, executionModules, hostedModules);
      Map<String, Path> resolved = new TreeMap<>();
      for (Artifact artifact : project.getArtifacts()) {
        if (artifact.getFile() == null) continue;
        String coordinate =
            artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
        resolved.put(coordinate, artifact.getFile().toPath());
      }
      Map<Path, String> coordinates = new HashMap<>();
      for (String coordinate : graph.artifacts().keySet()) {
        Path file = resolved.get(coordinate);
        if (file == null || !Files.isRegularFile(file) || !file.toString().endsWith(".jar"))
          throw new IllegalArgumentException("Missing resolved toolchain JAR: " + coordinate);
        if (coordinates.putIfAbsent(file, coordinate) != null)
          throw new IllegalArgumentException("Toolchain JAR has multiple coordinates: " + file);
      }
      var ownership =
          RuntimeModuleAssembler.assembleDependencies(
              List.copyOf(coordinates.keySet()), dependenciesDirectory.toPath());
      var artifacts = new ArrayList<ToolchainArtifactCatalogGenerator.Artifact>();
      Map<String, List<String>> mergedModules = new TreeMap<>();
      for (var entry : ownership.entrySet()) {
        String module = entry.getKey().getFileName().toString().replaceFirst("\\.jar$", "");
        List<String> components = entry.getValue().stream().map(coordinates::get).toList();
        String primary = null;
        for (Path component : entry.getValue()) {
          String identity =
              ModuleFinder.of(component).findAll().iterator().next().descriptor().name();
          if (identity.equals(module)) primary = coordinates.get(component);
        }
        if (primary == null)
          throw new IllegalArgumentException("Toolchain JAR has no primary component: " + module);
        artifacts.add(new ToolchainArtifactCatalogGenerator.Artifact(entry.getKey(), primary));
        if (components.size() > 1) {
          String owner = primary;
          mergedModules.put(
              owner.substring(0, owner.lastIndexOf(':')),
              components.stream()
                  .filter(component -> !component.equals(owner))
                  .map(component -> component.substring(0, component.lastIndexOf(':')))
                  .toList());
        }
      }
      ToolchainArtifactCatalogGenerator.generate(
          new ToolchainArtifactCatalogGenerator.Input(
              artifacts, graph.roots(), graph.dependencies(), graph.purposes(), mergedModules),
          resourcesDirectory.toPath().resolve("toolchain-artifacts.json"));
    } catch (DependencyGraphBuilderException | IOException | RuntimeException exception) {
      throw new MojoExecutionException(
          "Cannot generate resolved toolchain artifact catalog", exception);
    }
  }
}
