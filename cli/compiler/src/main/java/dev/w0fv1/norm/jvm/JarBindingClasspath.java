package dev.w0fv1.norm.jvm;

import java.nio.file.Path;
import java.util.List;

public final class JarBindingClasspath {
  private final ResolvedJarClasspath resolved;

  private JarBindingClasspath(ResolvedJarClasspath resolved) {
    this.resolved = resolved;
  }

  public static JarBindingClasspath prepare(List<ResolvedJarBinding> bindings) {
    return prepare(bindings, List.of());
  }

  public static JarBindingClasspath prepare(
      List<ResolvedJarBinding> bindings, List<ResolvedJarGraph> support) {
    var graphs =
        new java.util.ArrayList<>(bindings.stream().map(ResolvedJarBinding::graph).toList());
    graphs.addAll(support);
    return new JarBindingClasspath(ResolvedJarClasspath.resolve(graphs));
  }

  public static List<Path> resolve(List<ResolvedJarBinding> bindings) {
    return prepare(bindings).paths();
  }

  public static List<ResolvedJarArtifact> artifacts(List<ResolvedJarBinding> bindings) {
    return prepare(bindings).artifacts();
  }

  public JarBindingClasspath materialize(Path directory) throws java.io.IOException {
    return new JarBindingClasspath(resolved.materialize(directory));
  }

  public List<ResolvedJarArtifact> artifacts() {
    return resolved.artifacts();
  }

  public List<Path> paths() {
    return artifacts().stream().map(ResolvedJarArtifact::file).toList();
  }

  public static List<Path> processors(List<ResolvedJarBinding> bindings)
      throws java.io.IOException {
    return prepare(bindings).processors();
  }

  public List<Path> processors() throws java.io.IOException {
    var selected = resolved.artifacts();
    var services = JarServiceIndex.scan(selected.stream().map(ResolvedJarArtifact::file).toList());
    var providers =
        services.registrations().stream()
            .filter(
                registration ->
                    registration.service().equals("javax.annotation.processing.Processor"))
            .map(JarServiceIndex.Registration::artifact)
            .collect(java.util.stream.Collectors.toSet());
    if (providers.isEmpty()) return List.of();
    while (true) {
      var roots =
          selected.stream()
              .filter(artifact -> providers.contains(artifact.file().toAbsolutePath().normalize()))
              .map(ResolvedJarArtifact::identity)
              .toList();
      var classpath = resolved.closure(roots).stream().map(ResolvedJarArtifact::file).toList();
      var urls = new java.net.URL[classpath.size()];
      for (int index = 0; index < urls.length; index++)
        urls[index] = classpath.get(index).toUri().toURL();
      boolean changed = false;
      try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
        for (var registration : services.registrations()) {
          if (providers.contains(registration.artifact())) continue;
          try {
            Class.forName(registration.service(), false, loader);
            changed |= providers.add(registration.artifact());
          } catch (ClassNotFoundException absent) {
          }
        }
      }
      if (!changed) return classpath;
    }
  }
}
