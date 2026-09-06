package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JarBindingClasspathTest {
  @TempDir Path directory;

  @Test
  void reconcilesSupportAndApplicationDependenciesBeforeProducingPaths() throws Exception {
    var application = artifact("application", false);
    var support = artifact("support", false);
    var previous = artifact("shared", false);
    var file = artifact("new-shared", false);
    var selected =
        new ResolvedJarArtifact(
            new MavenJarIdentity(new MavenArtifactCoordinate("sample", "shared", "2")),
            file.file(),
            file.content());
    var supportGraph =
        new ResolvedJarGraph(
            support,
            List.of(support, previous),
            List.of(new JarDependencyEdge(support.identity(), previous.identity())));
    var applicationGraph =
        new ResolvedJarGraph(
            application,
            List.of(application, selected),
            List.of(new JarDependencyEdge(application.identity(), selected.identity())));
    var linked =
        JarBindingClasspath.prepare(List.of(binding(applicationGraph)), List.of(supportGraph));
    assertEquals(List.of(application, support, selected), linked.artifacts());
    assertEquals(List.of(application.file(), support.file(), selected.file()), linked.paths());
    assertEquals(List.of(), linked.processors());
  }

  @Test
  void derivesApplicationAndProcessorPathsFromOneSnapshot() throws Exception {
    var mixed = artifact("mixed", true);
    var runtime = artifact("runtime", false);
    var bindings =
        new java.util.ArrayList<>(
            List.of(
                binding(new ResolvedJarGraph(mixed, List.of(mixed), List.of())),
                binding(new ResolvedJarGraph(runtime, List.of(runtime), List.of()))));
    var classpath = JarBindingClasspath.prepare(bindings);
    bindings.clear();
    assertEquals(List.of(mixed, runtime), classpath.artifacts());
    assertEquals(List.of(mixed.file(), runtime.file()), classpath.paths());
    assertEquals(List.of(mixed.file()), classpath.processors());
  }

  @Test
  void includesProcessorExtensionsAndTheirTransitiveDependencies() throws Exception {
    var processor = artifact("processor", true);
    var extension =
        artifact(
            "extension",
            Map.of(
                "META-INF/services/java.util.spi.ToolProvider",
                "sample.Tool\n".getBytes(StandardCharsets.UTF_8)));
    var contract = new org.objectweb.asm.ClassWriter(0);
    contract.visit(
        org.objectweb.asm.Opcodes.V17,
        org.objectweb.asm.Opcodes.ACC_PUBLIC
            | org.objectweb.asm.Opcodes.ACC_INTERFACE
            | org.objectweb.asm.Opcodes.ACC_ABSTRACT,
        "sample/Extension",
        null,
        "java/lang/Object",
        null);
    contract.visitEnd();
    var helper =
        artifact("extension-helper", Map.of("sample/Extension.class", contract.toByteArray()));
    var secondary =
        artifact(
            "secondary",
            Map.of(
                "META-INF/services/sample.Extension",
                "sample.Secondary\n".getBytes(StandardCharsets.UTF_8)));
    var bindings =
        List.of(
            binding(new ResolvedJarGraph(processor, List.of(processor), List.of())),
            binding(
                new ResolvedJarGraph(
                    extension,
                    List.of(extension, helper),
                    List.of(new JarDependencyEdge(extension.identity(), helper.identity())))),
            binding(new ResolvedJarGraph(secondary, List.of(secondary), List.of())));
    assertEquals(
        java.util.Set.of(processor.file(), extension.file(), helper.file(), secondary.file()),
        java.util.Set.copyOf(JarBindingClasspath.processors(bindings)));
    assertEquals(List.of(), JarBindingClasspath.processors(List.of(bindings.get(1))));
  }

  @Test
  void processorPathUsesServiceDeclarationsAndTheirDependencyClosure() throws Exception {
    var mixed = artifact("mixed", true);
    var helper = artifact("helper", false);
    var runtime = artifact("processor-in-name-only", false);
    var bindings =
        List.of(
            binding(
                new ResolvedJarGraph(
                    mixed,
                    List.of(mixed, helper),
                    List.of(new JarDependencyEdge(mixed.identity(), helper.identity())))),
            binding(new ResolvedJarGraph(runtime, List.of(runtime), List.of())));
    assertEquals(List.of(mixed.file(), helper.file()), JarBindingClasspath.processors(bindings));
    assertEquals(
        List.of(mixed.file(), runtime.file(), helper.file()),
        JarBindingClasspath.resolve(bindings));
    assertEquals(List.of(), JarBindingClasspath.processors(List.of(bindings.get(1))));
  }

  private ResolvedJarArtifact artifact(String name, boolean processor) throws Exception {
    return artifact(
        name,
        processor
            ? Map.of(
                "META-INF/services/javax.annotation.processing.Processor",
                "sample.Processor\n".getBytes(StandardCharsets.UTF_8))
            : Map.of());
  }

  private ResolvedJarArtifact artifact(String name, Map<String, byte[]> entries) throws Exception {
    Path file = directory.resolve(name + ".jar");
    try (var output = new JarOutputStream(Files.newOutputStream(file))) {
      for (var entry : entries.entrySet()) {
        output.putNextEntry(new JarEntry(entry.getKey()));
        output.write(entry.getValue());
        output.closeEntry();
      }
    }
    return new ResolvedJarArtifact(
        new MavenJarIdentity(new MavenArtifactCoordinate("sample", name, "1")),
        file,
        Sha256Digest.compute(file));
  }

  private static ResolvedJarBinding binding(ResolvedJarGraph graph) {
    return new ResolvedJarBinding(
        graph,
        new JarApiSchema(List.of()),
        new GeneratedJarBinding(List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of()));
  }
}
