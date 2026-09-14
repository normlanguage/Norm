package dev.w0fv1.norm.project;

import dev.w0fv1.norm.jvm.GeneratedJarBinding;
import dev.w0fv1.norm.jvm.JarApiCache;
import dev.w0fv1.norm.jvm.JarBindingSourceGenerator;
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
    try {
      List<String> selectedTypes =
          descriptor.binding().orElseThrow().api().stream().map(JarBindingType::name).toList();
      var scanner =
          new JarApiCache(
              java.nio.file.Path.of(System.getProperty("user.home"), ".norm", "cache", "java-api"));
      var surface = scanner.scan(graph, selectedTypes, true);
      var api = scanner.scan(graph, selectedTypes, false);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generateSurface(
                  descriptor.coordinate(),
                  descriptor.exports().subList(0, descriptor.binding().orElseThrow().api().size()),
                  descriptor.binding().orElseThrow().api(),
                  graph.contentId(),
                  surface);
      return new ResolvedJarBinding(graph, api, generated);
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
