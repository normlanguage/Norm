package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaCollectionTaskInteropTest {
  @TempDir Path directory;

  @Test
  void snapshotsNullableListsAndOwnsNonblockingJavaCompletions() throws Exception {
    directory = Files.createDirectories(directory.resolve("interop"));
    var source =
        Files.writeString(
            directory.resolve("Interop.java"),
            """
        package sample;
        public final class Interop {
          private static final java.util.concurrent.CompletableFuture<String> pending = new java.util.concurrent.CompletableFuture<>();
          private static final java.util.concurrent.CompletableFuture<String> cancelled = new java.util.concurrent.CompletableFuture<>();
          private static final java.util.concurrent.CompletableFuture<String> owned = new java.util.concurrent.CompletableFuture<>();
          public static java.util.concurrent.CompletionStage<String> hostCancelled() { var value = new java.util.concurrent.CompletableFuture<String>(); value.cancel(true); return value; }
          public static java.util.List<String> values() { return new java.util.ArrayList<>(java.util.Arrays.asList("first", null, "last")); }
          public static java.util.List<String> empty() { return new java.util.ArrayList<>(); }
          public static java.util.concurrent.CompletionStage<String> ready() { return java.util.concurrent.CompletableFuture.completedFuture("ready"); }
          public static java.util.concurrent.CompletionStage<String> nullable() { return java.util.concurrent.CompletableFuture.completedFuture(null); }
          public static java.util.concurrent.CompletionStage<java.time.LocalDate> date() { return java.util.concurrent.CompletableFuture.completedFuture(java.time.LocalDate.of(2024, 2, 29)); }
          public static java.util.concurrent.CompletionStage<String> failed() { return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("host failed")); }
          public static java.util.concurrent.CompletionStage<String> pending() { return pending; }
          public static java.util.concurrent.CompletionStage<String> cancelStage() { return cancelled; }
          public static java.util.concurrent.CompletionStage<String> ownedStage() { return owned; }
          public static boolean cancelled() { return cancelled.isCancelled(); }
          public static boolean ownedCancelled() { return owned.isCancelled(); }
          public static boolean succeed() { return pending.complete("delayed"); }
        }
        """);
    var classes = Files.createDirectories(directory.resolve("classes"));
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), source.toString()));
    var jar = directory.resolve("interop.jar");
    try (var archive = new JarOutputStream(Files.newOutputStream(jar))) {
      archive.putNextEntry(new JarEntry("sample/Interop.class"));
      archive.write(Files.readAllBytes(classes.resolve("sample/Interop.class")));
      archive.closeEntry();
    }
    Files.writeString(
        directory.resolve("module.norm"),
        """
        Module module() { module(name: "interop", version: 1,
          binding: jarBinding(target: localJar(path: "interop.jar", integrity: sha256("%s")),
            api: [jarType(name: "sample.Interop", members: ["values", "empty", "ready", "nullable", "date", "failed", "pending", "cancelled", "cancelStage", "ownedStage", "ownedCancelled", "hostCancelled", "succeed"])])) }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    var entry =
        Files.writeString(
            directory.resolve("main.norm"),
            """
        package interop
        import std.collections.toList
        import std.concurrent.fromCompletionStage
        import std.concurrent.Task
        import std.concurrent.TaskExecutor
        import std.context.withContext
        import std.io.Resource
        import std.io.ResourceOwner
        import java.base.time.LocalDate
        class DeliveryQueue implements TaskExecutor {
          List<Function<Void()>> actions = []
          Void dispatch(Function<Void()> action) { actions.add(action) }
          Void drain() { for actions.size() > 0 { var current = actions actions = [] for action : current { action() } } }
        }
        class Owner implements ResourceOwner {
          Resource? current = null
          Integer registrations = 0
          Integer releases = 0
          Void own(Resource resource) { current = resource registrations = registrations + 1 }
          Void release(Resource resource) { current = null releases = releases + 1 }
          Void execute(Function<Void()> action) { withContext<ResourceOwner>(value: this, action: action) }
        }
        Void main() {
          var source = interopValues()!!
          List<String?> snapshot = toList(source)
          require(condition: snapshot == ["first", null, "last"], message: "order and null retained")
          source.set(arg0: 0, arg1: "changed")
          require(condition: snapshot[0] == "first", message: "snapshot independent of Java list")
          snapshot[2] = "snapshot"
          require(condition: source.get(arg0: 2) == "last", message: "Java list independent of snapshot")
          require(condition: toList(interopEmpty()!!).size() == 0, message: "empty snapshot")
          var ready = fromCompletionStage(interopReady()!!)
          require(condition: ready.await() == "ready", message: "completed Java stage")
          require(condition: fromCompletionStage(interopNullable()!!).await() == null, message: "nullable Java completion")
          LocalDate? date = fromCompletionStage(interopDate()!!).await()
          require(condition: date!!.getDayOfMonth() == 29, message: "canonical Java result identity")
          var failed = fromCompletionStage(interopFailed()!!)
          require(condition: failed.error { "recovered" }.await() == "recovered", message: "Java failure recovered")
          var queue = DeliveryQueue()
          var delayed = fromCompletionStage(stage: interopPending()!!, executor: queue)
          require(condition: !delayed.completed(), message: "bridge does not block on pending stage")
          var child = delayed.then<String>((String? result) { result!! + "-callback" })
          require(condition: interopSucceed(), message: "complete later")
          require(condition: !child.completed(), message: "callback waits for selected queue")
          queue.drain()
          require(condition: child.await() == "delayed-callback", message: "queued callback result")
          var cancelled = fromCompletionStage(interopCancelStage()!!)
          require(condition: cancelled.cancel() && interopCancelled(), message: "task cancellation reaches Java future")
          require(condition: fromCompletionStage(interopHostCancelled()!!).error { "cancelled" }.await() == "cancelled", message: "Java cancellation reaches task")
          var owner = Owner()
          owner.execute(() {
            var owned = fromCompletionStage(interopOwnedStage()!!)
            require(condition: owner.registrations == 1 && owner.current != null, message: "bridge owned by current scope")
            owner.current!!.close()
            require(condition: interopOwnedCancelled() && owner.releases == 1 && owner.current == null, message: "owner closes Java operation and releases once")
            owned.close()
            require(condition: owner.releases == 1, message: "closure is idempotent")
            var completed = fromCompletionStage(interopReady()!!)
            require(condition: owner.registrations == 2 && owner.releases == 2 && owner.current == null, message: "already completed stage releases ownership")
            require(condition: completed.await() == "ready", message: "released result remains readable")
          })
          printLine("interop-ok")
        }
        """);
    var runtime = new NormRuntime();
    var output = new StringWriter();
    try (var environment = ProjectEnvironment.bootstrap(runtime);
        var projects = environment.projectLoader(directory.resolve("cache"));
        var runner = new ApplicationRunner(projects, environment.compilerSession(), runtime)) {
      new ModuleBindingResolutionService(projects).resolve(directory.resolve("module.norm"));
      var result = runner.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("interop-ok" + System.lineSeparator(), output.toString());
  }
}
