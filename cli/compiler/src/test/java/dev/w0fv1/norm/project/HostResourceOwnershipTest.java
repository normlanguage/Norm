package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.jvm.JavaResourceOwnership;
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
          private boolean fail;
          public static Host broken() { var value = new Host(); value.fail = true; return value; }
          public Child child() { return child; }
          public Child[] children() { return new Child[] {child}; }
          public <T> T echo(T value) { return value; }
          public int total() { return closes * 100 + child.closes; }
          public void close() { closes++; child.close(); if (fail) throw new IllegalStateException("close failure"); }
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
              api: [jarType(name: "Host", members: ["new", "broken", "child", "children", "echo", "total", "close"], borrowed: ["child", "children"])]))
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
        var calls = restored.binding().orElseThrow().generated().calls().values();
        assertFalse(calls.isEmpty());
        assertEquals(
            java.util.Set.of("child", "children"),
            calls.stream()
                .filter(call -> call.ownership() == JavaResourceOwnership.BORROWED)
                .map(call -> call.name())
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(
            calls.stream()
                .filter(call -> !java.util.Set.of("child", "children").contains(call.name()))
                .allMatch(call -> call.ownership() == JavaResourceOwnership.OWNED));
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
        import std.io.ownResourceInContext
        import std.context.withContext
        import host.hostNew
        import host.hostBroken
        import java.base.lang.AutoCloseable
        import std.core.Exception
        import host.Host
        import host.Host_Child
        class Owner implements ResourceOwner {
          List<Resource> resources = []
          Integer accepted = 0
          Integer released = 0
          Void own(Resource resource) {
            accepted = accepted + 1
            resources.add(resource)
          }
          Void release(Resource resource) {
            released = released + 1
            resources = [for (item : resources) if (item != resource) item]
          }
          Void execute(Function<Void()> action) { action() }
          Void close() { var closing = resources for resource : closing { resource.close() } }
        }
        Void main() {
          var owner = Owner()
          var host = withContext<ResourceOwner, Host?>(value: owner, action: () { ownResourceInContext(hostNew()) })!!
          require(condition: owner.resources.size() == 1, message: "factory transferred to current owner")
          var children = host.children()!!
          var child = withContext<ResourceOwner, Host_Child>(value: owner,
            action: () { ownResourceInContext(children.get(0)!!) })
          require(condition: owner.resources.size() == 1, message: "borrowed array element does not acquire ownership")
          require(condition: ownResourceInContext(host.child()!!) == child, message: "array alias and borrowed getter share identity")
          Boolean borrowedDenied = false
          try { child.close() } catch Exception failure { borrowedDenied = true }
          require(condition: borrowedDenied && host.total() == 0,
            message: "borrowed child cannot close its owner's lifetime")
          var alias = withContext<ResourceOwner, Host_Child?>(value: owner, action: () { ownResourceInContext(host.echo(child)) })
          require(condition: alias == child, message: "generic alias preserves identity")
          require(condition: owner.resources.size() == 1, message: "borrowed child does not acquire ownership")
          withContext<ResourceOwner, Host>(value: owner, action: () { ownResourceInContext(host) })
          require(condition: owner.accepted == 1, message: "one host lifetime transfers only once")
          owner.close()
          require(condition: owner.resources.size() == 0, message: "closing releases current owner registration")
          host.close()
          require(condition: owner.released == 1, message: "canonical close releases its owner once")
          var direct = withContext<ResourceOwner, Host?>(value: owner,
            action: () { ownResourceInContext(hostNew()) })!!
          direct.close()
          require(condition: owner.resources.size() == 0 && owner.released == 2,
            message: "Java close releases the composed owner registration")
          AutoCloseable view = direct
          view.close()
          owner.close()
          require(condition: direct.total() == 101 && owner.released == 2,
            message: "direct Java close shares the canonical lifetime: total=${direct.total()}, released=${owner.released}")
          var failing = withContext<ResourceOwner, Host?>(value: owner,
            action: () { ownResourceInContext(hostBroken()) })!!
          Integer failures = 0
          try { failing.close() } catch Exception failure { failures = failures + 1 }
          try { failing.close() } catch Exception failure { failures = failures + 1 }
          require(condition: failures == 2 && failing.total() == 101 && owner.released == 3,
            message: "repeated close reports one failure without invoking the host twice")
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
