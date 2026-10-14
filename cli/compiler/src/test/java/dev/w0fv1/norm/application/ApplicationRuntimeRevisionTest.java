package dev.w0fv1.norm.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.store.DirectoryArtifactCache;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.ApplicationProgramData;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.runtime.PreparedApplicationContent;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationRuntimeRevisionTest {
  @TempDir Path directory;

  @Test
  void retainsForeignJavaClassIdentityAcrossBodyEditsAndNewNormClasses() throws Exception {
    Path classes = Files.createDirectories(directory.resolve("foo-classes"));
    Path source = directory.resolve("Foo.java");
    Files.writeString(
        source,
        "package fixture; public final class Foo { public Foo() {} public static int read(Foo value) { return 42; } public static String name(Class<?> value) { return value.getName(); } }");
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), source.toString()));
    Path workspace = Files.createDirectories(directory.resolve("workspace"));
    Path host = Files.createDirectories(workspace.resolve("dependencies/host"));
    Path jar = host.resolve("foo.jar");
    try (var archive = new JarOutputStream(Files.newOutputStream(jar))) {
      archive.putNextEntry(new JarEntry("fixture/Foo.class"));
      archive.write(Files.readAllBytes(classes.resolve("fixture/Foo.class")));
      archive.closeEntry();
    }
    Files.writeString(
        host.resolve("module.norm"),
        """
        Module module() { module(name: "host", version: 1, exports: ["Foo"],
          binding: jarBinding(target: localJar(path: "foo.jar", integrity: sha256("%s")),
            api: [jarType(name: "Foo", members: ["new", "read", "name"])])) }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path agent = Files.createDirectories(workspace.resolve("agent"));
    Files.writeString(
        agent.resolve("module.norm"),
        """
        Module module() { module(name: "agent", version: 1,
          dependencies: [dependency(repository: "github", name: "host", version: 1)]) }
        """);
    Path entry = agent.resolve("main.norm");
    String original =
        "package agent\nimport host.Foo\nimport host.fooRead\nimport host.fooName\npublic class Existing { public Integer number() { 1 } public Integer read(Foo foo) { fooRead(foo) } }\nVoid main() { fooName(Existing.class) }";
    Files.writeString(entry, original);
    try (var realm = new JvmJarBindingRuntime.Realm();
        var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var runner = ApplicationRunner.open(environment);
        var firstCompilation = runner.compileApplication(entry)) {
      assertTrue(
          firstCompilation.result().isSuccess(),
          firstCompilation.result().diagnostics().toString());
      try (var first =
          ApplicationRuntimeRevision.compiled(
              firstCompilation.application().orElseThrow(),
              ExecutionContext.builder().build(),
              realm)) {
        var firstRuntime = (JvmJarBindingRuntime) first.context().jarBindingRuntime();
        Class<?> fooType = firstRuntime.applicationClassLoader().loadClass("fixture.Foo");
        Object foo = fooType.getDeclaredConstructor().newInstance();
        for (String candidateSource :
            List.of(
                original.replace("{ 1 }", "{ 2 }"),
                original
                        .replace("{ 1 }", "{ 3 }")
                        .replace(
                            "fooName(Existing.class)",
                            "fooName(Existing.class)\nfooName(Added.class)")
                    + "\npublic class Added { Integer value }")) {
          Files.writeString(entry, candidateSource);
          try (var compilation = runner.compileApplication(entry)) {
            assertTrue(
                compilation.result().isSuccess(), compilation.result().diagnostics().toString());
            try (var candidate =
                ApplicationRuntimeRevision.compiled(
                    compilation.application().orElseThrow(),
                    ExecutionContext.builder().build(),
                    realm)) {
              var runtime = (JvmJarBindingRuntime) candidate.context().jarBindingRuntime();
              assertSame(fooType, runtime.applicationClassLoader().loadClass("fixture.Foo"));
              assertEquals(
                  42,
                  runtime
                      .applicationClassLoader()
                      .loadClass("fixture.Foo")
                      .getMethod("read", fooType)
                      .invoke(null, foo));
              if (candidateSource.contains("class Added"))
                assertNotNull(runtime.applicationClassLoader().loadClass("agent.Added"));
            }
          }
        }
      }
    }
  }

  @Test
  void changedJavaRealmsCoexistUntilEachLastLeaseIsReleased() throws Exception {
    var cache = new DirectoryArtifactCache(directory.resolve("cache"), 8, 1048576);
    var program = program();
    var firstContent =
        new PreparedApplicationContent(
            program, Map.of(), List.of(FileSnapshot.capture(jar("one", "old"))), List.of());
    var secondContent =
        new PreparedApplicationContent(
            program, Map.of(), List.of(FileSnapshot.capture(jar("two", "new"))), List.of());
    var firstAssets = firstContent.acquire(cache);
    var secondAssets = secondContent.acquire(cache);
    var first =
        ApplicationRuntimeRevision.prepared(
            firstContent,
            firstAssets,
            ExecutionContext.builder().build(),
            new JvmJarBindingRuntime.Realm());
    var second =
        ApplicationRuntimeRevision.prepared(
            secondContent,
            secondAssets,
            ExecutionContext.builder().build(),
            new JvmJarBindingRuntime.Realm());
    var oldCallback = first.retain();
    var oldRuntime = (JvmJarBindingRuntime) first.context().jarBindingRuntime();
    var newRuntime = (JvmJarBindingRuntime) second.context().jarBindingRuntime();
    var oldClass = oldRuntime.applicationClassLoader().loadClass("DynamicDependency");
    var newClass = newRuntime.applicationClassLoader().loadClass("DynamicDependency");
    assertNotSame(oldClass, newClass);
    assertEquals("old", oldClass.getMethod("message").invoke(null));
    assertEquals("new", newClass.getMethod("message").invoke(null));
    first.close();
    assertNotNull(firstAssets.path());
    assertEquals(
        "old",
        ((JvmJarBindingRuntime) oldCallback.context().jarBindingRuntime())
            .applicationClassLoader()
            .loadClass("DynamicDependency")
            .getMethod("message")
            .invoke(null));
    second.close();
    assertThrows(IllegalStateException.class, secondAssets::path);
    assertThrows(
        dev.w0fv1.norm.execution.JarBindingRuntimeException.class,
        newRuntime::applicationClassLoader);
    assertNotNull(firstAssets.path());
    oldCallback.close();
    assertThrows(IllegalStateException.class, firstAssets::path);
    assertThrows(
        dev.w0fv1.norm.execution.JarBindingRuntimeException.class,
        oldRuntime::applicationClassLoader);
  }

  @Test
  void JavaStaticCallbackCyclesDoNotKeepRetiredRealmAssetsAlive() throws Exception {
    var cache = new DirectoryArtifactCache(directory.resolve("cycle-cache"), 8, 1048576);
    var content =
        new PreparedApplicationContent(
            program(), Map.of(), List.of(FileSnapshot.capture(jar("cycle", "cycle"))), List.of());
    var assets = content.acquire(cache);
    var valueReference =
        new java.util.concurrent.atomic.AtomicReference<java.lang.ref.WeakReference<Object>>();
    var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    var creator =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    var revision =
                        ApplicationRuntimeRevision.prepared(
                            content,
                            assets,
                            ExecutionContext.builder().build(),
                            new JvmJarBindingRuntime.Realm());
                    var runtime = (JvmJarBindingRuntime) revision.context().jarBindingRuntime();
                    var type = runtime.applicationClassLoader().loadClass("DynamicDependency");
                    Object value = type.getDeclaredConstructor().newInstance();
                    type.getField("owner").set(value, revision);
                    type.getField("keep").set(null, value);
                    revision.retain(value);
                    revision.close();
                    assertNotNull(assets.path());
                    valueReference.set(new java.lang.ref.WeakReference<>(value));
                  } catch (Throwable exception) {
                    failure.set(exception);
                  }
                });
    creator.join();
    if (failure.get() != null) throw new AssertionError(failure.get());
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(15),
        () -> {
          boolean closed = false;
          while (!closed) {
            System.gc();
            Thread.sleep(50);
            try {
              assets.path();
            } catch (IllegalStateException exception) {
              closed = true;
            }
          }
        });
    assertNull(valueReference.get().get());
  }

  private ApplicationProgramData program() throws Exception {
    try (var compiler = new CompilerSession()) {
      var compilation =
          compiler.compile(SourceFile.of(directory.resolve("main.norm"), "Void main() {}"));
      assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
      var artifact = compilation.output().orElseThrow().artifact();
      return new ApplicationProgramData(
          artifact, CoreExecutionPlan.forArtifact(artifact), List.of(), "");
    }
  }

  private Path jar(String name, String message) throws Exception {
    Path root = Files.createDirectories(directory.resolve(name));
    Path source = root.resolve("DynamicDependency.java");
    Files.writeString(
        source,
        "public final class DynamicDependency { public Object owner; public static Object keep; public static String message() { return \""
            + message
            + "\"; } }");
    Path classes = Files.createDirectories(root.resolve("classes"));
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), source.toString()));
    Path jar = root.resolve("dependency.jar");
    try (var archive = new JarOutputStream(Files.newOutputStream(jar))) {
      archive.putNextEntry(new JarEntry("DynamicDependency.class"));
      archive.write(Files.readAllBytes(classes.resolve("DynamicDependency.class")));
      archive.closeEntry();
    }
    return jar;
  }
}
