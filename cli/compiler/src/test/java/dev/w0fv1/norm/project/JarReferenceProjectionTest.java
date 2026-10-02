package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JarReferenceProjectionTest {
  @TempDir Path directory;

  @Test
  void preservesConcreteSubtypesThroughAbstractAndCollectionReturns() throws Exception {
    Path classes = Files.createDirectories(directory.resolve("classes"));
    Path source = directory.resolve("Projection.java");
    Files.writeString(
        source,
        """
        package fixture;
        public final class Projection {
          private final Leaf content = new Leaf();
          public Node content() { return content; }
          public java.util.List<Node> contents() { return java.util.List.of(content); }
          public static abstract class Node { public abstract String kind(); }
          public static class Pane extends Node { public String kind() { return "pane"; } }
          public static final class Leaf extends Pane { public String kind() { return "leaf"; } }
        }
        """);
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), source.toString()));
    Path root = Files.createDirectories(directory.resolve("sample/projection"));
    Path jarPath = root.resolve("projection.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(jarPath));
        var files = Files.walk(classes)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
        Files.copy(file, jar);
        jar.closeEntry();
      }
    }
    Files.writeString(
        root.resolve("module.norm"),
        """
        Module module() {
          module(name: "sample.projection", version: 1,
            binding: jarBinding(target: localJar(path: "projection.jar", integrity: sha256("%s")),
              api: [jarType(name: "Projection", members: ["new", "content", "contents"], borrowed: ["content"]),
                jarType(name: "Projection.Node", members: ["kind"]),
                jarType(name: "Projection.Pane", members: ["kind"]),
                jarType(name: "Projection.Leaf", members: ["kind"])]))
        }
        """
            .formatted(Sha256Digest.compute(jarPath).value()));
    Path entry = root.resolve("main.norm");
    Files.writeString(
        entry,
        """
        package sample.projection
        Void main() {
          var host = projectionNew()!!
          switch host.content()!! {
            case ProjectionLeaf leaf { printLine(leaf.kind()!!) }
            case _ { require(condition: false, message: "abstract getter erased the concrete subtype") }
          }
          var contents = host.contents()!!
          switch contents.get(0)!! {
            case ProjectionLeaf leaf { printLine(leaf.kind()!!) }
            case _ { require(condition: false, message: "collection getter erased the concrete subtype") }
          }
        }
        """);
    var backend = new NormRuntime();
    var environment = ProjectEnvironment.bootstrap(backend);
    var output = new StringWriter();
    try (var runner =
        new ApplicationRunner(
            environment.projectLoader(directory.resolve("cache")),
            environment.compilerSession(),
            backend)) {
      var result = runner.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals(
        "leaf" + System.lineSeparator() + "leaf" + System.lineSeparator(), output.toString());
  }
}
