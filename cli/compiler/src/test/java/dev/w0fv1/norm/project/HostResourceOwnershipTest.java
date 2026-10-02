package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.testing.MavenTestRepository;
import dev.w0fv1.norm.value.Sha256Digest;
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

final class HostResourceOwnershipTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void scopedFactoryBorrowedChildAndGenericAliasShareOneLifetime(boolean packaged)
      throws Exception {
    Path classes = Files.createDirectories(directory.resolve("classes"));
    Path source = directory.resolve("Host.java");
    Files.writeString(
        source,
        """
        package fixture;
        public final class Host implements AutoCloseable {
          private final Child child = new Child();
          private int closes;
          public Child child() { return child; }
          public Child[] children() { return new Child[] {child}; }
          public <T> T echo(T value) { return value; }
          public int total() { return closes * 100 + child.closes; }
          public void close() { closes++; child.close(); }
          public static final class Child implements AutoCloseable {
            private int closes;
            public void close() { closes++; }
          }
        }
        """);
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), source.toString()));
    Path workspace = Files.createDirectories(directory.resolve("workspace"));
    Path binding = Files.createDirectories(workspace.resolve("dependencies/host"));
    Path jarPath = binding.resolve("host.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(jarPath));
        var files = Files.walk(classes)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
        Files.copy(file, jar);
        jar.closeEntry();
      }
    }
    Path repository = directory.resolve("repository");
    Path maven = Files.createDirectories(repository.resolve("fixture/host/1"));
    Files.copy(jarPath, maven.resolve("host-1.jar"));
    Files.writeString(
        maven.resolve("host-1.pom"),
        "<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>host</artifactId><version>1</version></project>");
    Files.writeString(
        binding.resolve("module.norm"),
        """
        Module module() {
          module(name: "host", version: 1,
            binding: jarBinding(target: mavenJar(group: "fixture", artifact: "host", version: "1", resolution: sha256("%s")),
              api: [jarType(name: "Host", members: ["new", "child", "children", "echo", "total", "close"], borrowed: ["child", "children"])]))
        }
        """
            .formatted(Sha256Digest.compute(jarPath).value()));
    var backend = new NormRuntime();
    var environment = ProjectEnvironment.bootstrap(backend);
    if (packaged) {
      try (var compiler = environment.compilerSession();
          var projects = environment.projectLoader(repository)) {
        var module =
            new ModulePackager(projects, compiler)
                .packageModule(binding.resolve("module.norm"), repository);
        var restored = new ModuleArchiveReader().read(module.archive());
        assertEquals(
            java.util.List.of("child", "children"),
            restored.descriptor().binding().orElseThrow().api().getFirst().borrowed());
      }
    }
    Path app =
        Files.createDirectories(
            packaged ? directory.resolve("consumer/app") : workspace.resolve("app"));
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() { module(name: "app", version: 1,
          dependencies: [dependency(repository: "github", name: "host", version: 1)]) }
        """);
    Path entry = app.resolve("main.norm");
    Files.writeString(
        entry,
        """
        package app
        import std.io.Resource
        import std.io.ResourceOwner
        import std.context.withContext
        import host.hostNew
        import host.Host
        import host.Host_Child
        class Owner implements ResourceOwner {
          List<Resource> resources = []
          Void own(Resource resource) { resources.add(resource) }
          Void release(Resource resource) { resources = [for (item : resources) if (item != resource) item] }
          Void execute(Function<Void()> action) { action() }
          Void close() { var closing = resources for resource : closing { resource.close() } }
        }
        Void main() {
          var owner = Owner()
          var host = withContext<ResourceOwner, Host?>(value: owner, action: () { hostNew() })!!
          require(condition: owner.resources.size() == 1, message: "factory transferred to current owner")
          var children = host.children()!!
          var child = children.get(0)!!
          require(condition: owner.resources.size() == 1, message: "borrowed array element does not acquire ownership")
          require(condition: host.child()!! == child, message: "array alias and borrowed getter share identity")
          var alias = withContext<ResourceOwner, Host_Child?>(value: owner, action: () { host.echo(child) })
          require(condition: alias == child, message: "generic alias preserves identity")
          require(condition: owner.resources.size() == 1, message: "borrowed child does not acquire ownership")
          owner.close()
          require(condition: owner.resources.size() == 0, message: "closing releases current owner registration")
          host.close()
          printLine(host.total())
        }
        """);
    var output = new StringWriter();
    try (var runner =
        packaged
            ? new ApplicationRunner(
                environment.projectLoader(MavenTestRepository.prepare(repository)),
                environment.compilerSession(),
                backend)
            : new ApplicationRunner(
                environment.projectLoader(repository), environment.compilerSession(), backend)) {
      var result = runner.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("101" + System.lineSeparator(), output.toString());
  }
}
