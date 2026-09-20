package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class CrossModuleJarBindingTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"direct", "transitive", "diamond"})
  void sharesPublicJavaTypesInSourceAndPackagedModules(String dependencyShape) throws Exception {
    var repository = directory.resolve("repository");
    var javaSources = Files.createDirectories(directory.resolve("java/sample"));
    var classes = Files.createDirectories(directory.resolve("classes"));
    Files.writeString(
        javaSources.resolve("Node.java"),
        """
        package sample;
        public class Node<T> {
          public String text() { return "cross-module"; }
          @Override public boolean equals(Object other) { return other instanceof Node; }
          @Override public int hashCode() { return 1; }
        }
        """);
    Files.writeString(
        javaSources.resolve("Host.java"),
        """
        package sample;
        public final class Host<T> extends %s<T> {
          public static <T> Node<T> echo(Node<T> node) { return node; }
        }
        class Hidden<T> extends Node<T> {}
        """
            .formatted(dependencyShape.equals("direct") ? "Node" : "Hidden"));
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-d",
                classes.toString(),
                javaSources.resolve("Node.java").toString(),
                javaSources.resolve("Host.java").toString()));
    for (var name : java.util.List.of("Node", "Host")) {
      var artifact =
          Files.createDirectories(repository.resolve("fixture/" + name.toLowerCase() + "/1"));
      try (var jar =
          new JarOutputStream(
              Files.newOutputStream(artifact.resolve(name.toLowerCase() + "-1.jar")))) {
        jar.putNextEntry(new JarEntry("sample/" + name + ".class"));
        jar.write(Files.readAllBytes(classes.resolve("sample/" + name + ".class")));
        jar.closeEntry();
        if (name.equals("Host")) {
          jar.putNextEntry(new JarEntry("sample/Hidden.class"));
          jar.write(Files.readAllBytes(classes.resolve("sample/Hidden.class")));
          jar.closeEntry();
        }
      }
      var dependency =
          name.equals("Host")
              ? "<dependencies><dependency><groupId>fixture</groupId><artifactId>node</artifactId><version>1</version></dependency></dependencies>"
              : "";
      Files.writeString(
          artifact.resolve(name.toLowerCase() + "-1.pom"),
          "<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>"
              + name.toLowerCase()
              + "</artifactId><version>1</version>"
              + dependency
              + "</project>");
    }
    var root = Files.createDirectories(directory.resolve("workspace"));
    var widgets = Files.createDirectories(root.resolve("dependencies/widgets"));
    var host = Files.createDirectories(root.resolve("dependencies/host"));
    var forwardingModules = new java.util.ArrayList<Path>();
    String hostDependencies = "dependency(repository: \"github\", name: \"widgets\", version: 1)";
    if (!dependencyShape.equals("direct")) {
      var forwardingCount = dependencyShape.equals("diamond") ? 2 : 1;
      var forwarded = new java.util.ArrayList<String>();
      for (int index = 0; index < forwardingCount; index++) {
        String name = "platform" + index;
        var platform = Files.createDirectories(root.resolve("dependencies/" + name));
        Files.writeString(
            platform.resolve("module.norm"),
            """
            Module module() { module(name: "%s", version: 1, exports: [],
              dependencies: [exportedDependency(repository: "github", name: "widgets", version: 1)]) }
            """
                .formatted(name));
        forwardingModules.add(platform);
        forwarded.add("dependency(repository: \"github\", name: \"" + name + "\", version: 1)");
      }
      hostDependencies = String.join(", ", forwarded);
    }
    Files.writeString(
        widgets.resolve("module.norm"),
        """
        Module module() { module(name: "widgets", version: 1,
          exports: ["Widget"], binding: jarBinding(target: mavenJar(group: "fixture", artifact: "node", version: "1"),
            api: [jarType(name: "Node", members: ["new", "text"])])) }
        """);
    Files.writeString(
        host.resolve("module.norm"),
        """
        Module module() { module(name: "host", version: 1,
          dependencies: [%s],
          binding: jarBinding(target: mavenJar(group: "fixture", artifact: "host", version: "1"),
            api: [jarType(name: "Host", members: ["new", "echo"])])) }
        """
            .formatted(hostDependencies));
    var app = Files.createDirectories(root.resolve("app"));
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() { module(name: "app", version: 1, dependencies: [
          dependency(repository: "github", name: "widgets", version: 1),
          dependency(repository: "github", name: "host", version: 1)]) }
        """);
    var entry = app.resolve("main.norm");
    Files.writeString(
        entry,
        """
        package app
        import widgets.Widget
        import host.hostEcho
        import host.hostNew
        Void main() {
          Widget<String?> original = hostNew<String>()
          Widget<String?> returned = hostEcho<String>(original)!!
          require(condition: returned == original, message: "Java identity survives superclass views")
          require(condition: returned != hostNew<String>(), message: "distinct Java instances retain identity")
          Set<Any> identities = Set<>()
          identities.add(original)
          require(condition: identities.contains(returned), message: "Java identity hash survives superclass views")
          printLine(returned.text()!!)
        }
        """);
    var backend = new NormRuntime();
    var environment = ProjectEnvironment.bootstrap(backend);
    try (var projects = environment.projectLoader(repository)) {
      for (var module : java.util.List.of(widgets, host)) {
        new ModuleBindingResolutionService(projects).resolve(module.resolve("module.norm"));
      }
    }
    assertEquals(
        "cross-module" + System.lineSeparator(), run(environment, backend, repository, entry));
    if (dependencyShape.equals("direct")) {
      var aliases = Files.createDirectories(root.resolve("dependencies/aliases"));
      Files.writeString(
          aliases.resolve("module.norm"),
          Files.readString(widgets.resolve("module.norm"))
              .replace("name: \"widgets\"", "name: \"aliases\""));
      var descriptor = host.resolve("module.norm");
      String valid = Files.readString(descriptor);
      Files.writeString(
          descriptor,
          valid.replace(
              "dependencies: [",
              "dependencies: [dependency(repository: \"github\", name: \"aliases\", version: 1), "));
      try (var projects = environment.projectLoader(repository)) {
        var failure = assertThrows(java.io.IOException.class, () -> projects.load(entry));
        assertTrue(
            failure.getMessage().contains("ambiguous public Java type sample.Node"),
            failure.getMessage());
        assertTrue(failure.getMessage().contains("widgets"), failure.getMessage());
        assertTrue(failure.getMessage().contains("aliases"), failure.getMessage());
        assertThrows(java.io.IOException.class, () -> projects.loadForAnalysis(entry));
      } finally {
        Files.writeString(descriptor, valid);
      }
    }
    Path hostArchive;
    try (var compiler = environment.compilerSession();
        var projects = environment.projectLoader(repository)) {
      var packager = new ModulePackager(projects, compiler);
      packager.packageModule(widgets.resolve("module.norm"), repository);
      for (var forwarding : forwardingModules) {
        packager.packageModule(forwarding.resolve("module.norm"), repository);
      }
      hostArchive = packager.packageModule(host.resolve("module.norm"), repository).archive();
    }
    Files.move(widgets, root.resolve("packaged-widgets"));
    try (var projects = environment.projectLoader(repository)) {
      var analysis = projects.loadForAnalysis(entry);
      var compiled = environment.compilerSession().compile(analysis.analysisCompilationRequest());
      assertTrue(compiled.isSuccess(), () -> compiled.diagnostics().toString());
    }
    Files.move(root.resolve("dependencies"), root.resolve("packaged-sources"));
    assertEquals(
        "cross-module" + System.lineSeparator(), run(environment, backend, repository, entry));
    try (var archive = java.nio.file.FileSystems.newFileSystem(hostArchive)) {
      var generated = archive.getPath("/sources/host/Host.norm");
      Files.writeString(generated, Files.readString(generated).replace("hostEcho", "hostTampered"));
    }
    Files.writeString(
        hostArchive.resolveSibling(hostArchive.getFileName() + ".sha256"),
        dev.w0fv1.norm.value.Sha256Digest.compute(hostArchive).value() + "\n");
    try (var projects = environment.projectLoader(repository)) {
      var failure = assertThrows(java.io.IOException.class, () -> projects.load(entry));
      assertTrue(
          failure.getMessage().contains("generated sources do not match"), failure.getMessage());
    }
  }

  private static String run(
      ProjectEnvironment environment, NormRuntime backend, Path repository, Path entry)
      throws Exception {
    var output = new StringWriter();
    try (var launcher =
        new ApplicationRunner(
            environment.projectLoader(repository), environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    return output.toString();
  }
}
