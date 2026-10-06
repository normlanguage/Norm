package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class ProjectLoadFailureInputsTest {
  @TempDir Path temporaryDirectory;

  @Test
  void retainsAllReadInputsWhenDependencySupportedBindingProjectionFails() throws Exception {
    Path provider = Files.createDirectories(temporaryDirectory.resolve("dependencies/provider"));
    var dependencyBox = new ClassWriter(0);
    dependencyBox.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC, "other/Box", null, "java/lang/Object", null);
    dependencyBox.visitEnd();
    Path providerJar =
        archive(
            provider.resolve("provider.jar"),
            Map.of("other/Box.class", dependencyBox.toByteArray()));
    Path providerModule =
        Files.writeString(
            provider.resolve("module.norm"),
            """
        Module module() { return module(name: "provider", version: 1,
          binding: jarBinding(target: localJar(path: "provider.jar", integrity: sha256("%s")),
            api: [jarType(name: "other.Box", members: [])])) }
        """
                .formatted(Sha256Digest.compute(providerJar).value()));
    Path root = Files.createDirectories(temporaryDirectory.resolve("sample"));
    var child = new ClassWriter(0);
    child.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
        "sample/Box",
        null,
        "java/lang/Object",
        null);
    child.visitEnd();
    Path childJar =
        archive(root.resolve("child.jar"), Map.of("sample/Box.class", child.toByteArray()));
    Path module =
        Files.writeString(
            root.resolve("module.norm"),
            """
        Module module() { return module(name: "sample", version: 1,
          dependencies: [dependency(repository: "github", name: "provider", version: 1)],
          binding: jarBinding(target: localJar(path: "child.jar", integrity: sha256("%s")),
            api: [jarType(name: "Box", members: [])])) }
        """
                .formatted(Sha256Digest.compute(childJar).value()));
    Path entry = Files.writeString(root.resolve("Main.norm"), "package sample Void main() {}");
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var projects = environment.projectLoader(temporaryDirectory.resolve("cache"))) {
      var failure = assertThrows(ProjectLoadException.class, () -> projects.load(entry));
      assertInstanceOf(IllegalArgumentException.class, failure.getCause());
      assertTrue(
          failure.getMessage().contains("must identify exactly one dependency graph class"),
          failure.getMessage());
      for (Path input : java.util.List.of(entry, module, childJar, providerModule, providerJar))
        assertTrue(failure.inputs().affects(input), input.toString());
      var cancelled =
          new dev.w0fv1.norm.frontend.CompilationControl(
              () -> true, dev.w0fv1.norm.frontend.CompilationLimits.standard());
      assertThrows(
          dev.w0fv1.norm.frontend.CompilationCancelledException.class,
          () ->
              projects.loadForAnalysis(
                  dev.w0fv1.norm.source.SourceFile.read(entry), java.util.List.of(), cancelled));
    }
  }

  private Path archive(Path path, Map<String, byte[]> entries) throws Exception {
    try (var archive = new JarOutputStream(Files.newOutputStream(path))) {
      for (var entry : entries.entrySet()) {
        archive.putNextEntry(new JarEntry(entry.getKey()));
        archive.write(entry.getValue());
        archive.closeEntry();
      }
    }
    return path;
  }
}
