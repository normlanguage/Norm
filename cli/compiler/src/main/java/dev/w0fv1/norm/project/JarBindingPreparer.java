package dev.w0fv1.norm.project;

import dev.w0fv1.norm.jvm.GeneratedJarBinding;
import dev.w0fv1.norm.jvm.JarApiCache;
import dev.w0fv1.norm.jvm.JarBindingSourceGenerator;
import dev.w0fv1.norm.jvm.JavaApiScanInput;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleDescriptor;
import java.io.IOException;
import java.util.List;

final class JarBindingPreparer {
  private JarBindingPreparer() {}

  static ResolvedJarBinding prepare(ModuleDescriptor descriptor, ResolvedJarGraph graph)
      throws IOException {
    return prepare(descriptor, graph, java.util.Map.of(), List.of());
  }

  static ResolvedJarBinding prepare(
      ModuleDescriptor descriptor,
      ResolvedJarGraph graph,
      java.util.Map<String, dev.w0fv1.norm.execution.JarBindingClassReference.Nominal> imports,
      List<ResolvedJarGraph> supportingGraphs)
      throws IOException {
    try {
      List<String> selectedTypes =
          descriptor.binding().orElseThrow().api().stream().map(JarBindingType::name).toList();
      var scanner =
          new JarApiCache(
              java.nio.file.Path.of(System.getProperty("user.home"), ".norm", "cache", "java-api"));
      var scanInput = new JavaApiScanInput(graph, supportingGraphs);
      var surface = scanner.scan(scanInput, selectedTypes, true);
      var apiTypes =
          descriptor.name().equals("java.base")
              ? java.util.stream.Stream.concat(
                      selectedTypes.stream(),
                      dev.w0fv1.norm.jvm.JavaScalarConformances.canonicalTypes().stream())
                  .distinct()
                  .toList()
              : selectedTypes;
      var api = scanner.scan(scanInput, apiTypes, false);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generateSurface(
                  descriptor.coordinate(),
                  descriptor.exports().subList(0, descriptor.binding().orElseThrow().api().size()),
                  descriptor.binding().orElseThrow().api(),
                  graph.contentId(),
                  surface,
                  imports);
      return new ResolvedJarBinding(graph, api, generated, imports);
    } catch (IllegalArgumentException exception) {
      throw new IOException(
          "cannot generate JAR binding for "
              + descriptor.name()
              + "@"
              + descriptor.version()
              + ": "
              + exception.getMessage(),
          exception);
    }
  }
}
