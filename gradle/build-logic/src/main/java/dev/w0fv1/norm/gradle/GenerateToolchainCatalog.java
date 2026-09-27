package dev.w0fv1.norm.gradle;

import dev.w0fv1.norm.packaging.RuntimeModuleAssembler;
import dev.w0fv1.norm.packaging.RuntimeStorage;
import dev.w0fv1.norm.packaging.ToolchainArtifactCatalogGenerator;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class GenerateToolchainCatalog extends DefaultTask {
  @Classpath
  public abstract ConfigurableFileCollection getResolvedArtifacts();

  @Input
  public abstract Property<String> getResolutionIdentity();

  @Input
  public abstract Property<String> getPurposeIdentity();

  @Input
  public abstract Property<String> getStorage();

  @LocalState
  public abstract DirectoryProperty getStaging();

  @OutputFile
  public abstract RegularFileProperty getDestination();

  @TaskAction
  public void generate() throws IOException {
    Configuration runtime = getProject().getConfigurations().getByName("runtimeClasspath");
    Map<String, List<String>> graph = resolvedGraph(runtime);
    var raw =
        runtime
            .getIncoming()
            .artifactView(
                view ->
                    view.attributes(
                        attributes ->
                            attributes.attribute(Attribute.of("javaModule", Boolean.class), false)))
            .getArtifacts();
    Map<Path, String> coordinates = new HashMap<>();
    for (var artifact : raw.getArtifacts())
      coordinates.put(
          artifact.getFile().toPath(), coordinate(artifact.getId().getComponentIdentifier()));
    var ownership =
        RuntimeModuleAssembler.assembleDependencies(
            List.copyOf(coordinates.keySet()),
            getStaging().get().getAsFile().toPath(),
            RuntimeStorage.parse(getStorage().get()));
    List<ToolchainArtifactCatalogGenerator.Artifact> artifacts = new ArrayList<>();
    Map<String, List<String>> merged = new TreeMap<>();
    for (var item : ownership.entrySet()) {
      String name = item.getKey().getFileName().toString().replaceFirst("\\.jar$", "");
      String primary = null;
      for (Path component : item.getValue()) {
        String module = ModuleFinder.of(component).findAll().iterator().next().descriptor().name();
        if (module.equals(name)) primary = coordinates.get(component);
      }
      if (primary == null)
        throw new IllegalArgumentException("Toolchain JAR has no primary component: " + name);
      artifacts.add(new ToolchainArtifactCatalogGenerator.Artifact(item.getKey(), primary));
      if (item.getValue().size() > 1) {
        String owner = primary;
        merged.put(
            module(owner),
            item.getValue().stream()
                .map(coordinates::get)
                .filter(value -> !value.equals(owner))
                .map(GenerateToolchainCatalog::module)
                .toList());
      }
    }
    List<String> roots = graph.get("");
    Set<String> execution = direct("nativeExecution", "nativeExecutionRuntime");
    Set<String> hosted = direct("nativeHosted");
    Map<String, List<String>> purposes =
        Map.of(
            "execution", roots.stream().filter(value -> execution.contains(module(value))).toList(),
            "hosted", roots.stream().filter(value -> hosted.contains(module(value))).toList(),
            "tooling",
                roots.stream()
                    .filter(
                        value ->
                            !execution.contains(module(value)) && !hosted.contains(module(value)))
                    .toList());
    ToolchainArtifactCatalogGenerator.generate(
        new ToolchainArtifactCatalogGenerator.Input(
            artifacts,
            roots,
            graph.entrySet().stream()
                .filter(entry -> !entry.getKey().isEmpty())
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
            purposes,
            merged),
        getDestination().get().getAsFile().toPath());
  }

  public static Map<String, List<String>> resolvedGraph(Configuration runtime) {
    ResolvedComponentResult root =
        runtime.getIncoming().getResolutionResult().getRootComponent().get();
    Map<String, List<String>> graph = new TreeMap<>();
    ArrayDeque<ResolvedComponentResult> pending = new ArrayDeque<>();
    pending.add(root);
    while (!pending.isEmpty()) {
      ResolvedComponentResult component = pending.removeFirst();
      String identity = component == root ? "" : coordinate(component.getId());
      if (graph.containsKey(identity)) continue;
      List<String> children = new ArrayList<>();
      for (var dependency : component.getDependencies()) {
        if (dependency.isConstraint()) continue;
        if (!(dependency instanceof ResolvedDependencyResult resolved))
          throw new IllegalArgumentException(
              "Unresolved toolchain dependency: " + dependency.getRequested());
        children.add(coordinate(resolved.getSelected().getId()));
        pending.add(resolved.getSelected());
      }
      graph.put(identity, children.stream().distinct().sorted().toList());
    }
    return java.util.Collections.unmodifiableMap(graph);
  }

  private Set<String> direct(String... names) {
    Set<String> modules = new HashSet<>();
    for (String name : names)
      getProject()
          .getConfigurations()
          .getByName(name)
          .getDependencies()
          .forEach(dependency -> modules.add(dependency.getGroup() + ":" + dependency.getName()));
    return modules;
  }

  private static String coordinate(
      org.gradle.api.artifacts.component.ComponentIdentifier identity) {
    if (!(identity instanceof ModuleComponentIdentifier module))
      throw new IllegalArgumentException("Toolchain component has no module identity: " + identity);
    return module.getGroup() + ":" + module.getModule() + ":" + module.getVersion();
  }

  private static String module(String coordinate) {
    return coordinate.substring(0, coordinate.lastIndexOf(':'));
  }
}
