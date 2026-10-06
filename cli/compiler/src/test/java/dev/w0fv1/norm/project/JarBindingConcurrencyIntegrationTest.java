package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@Timeout(60)
final class JarBindingConcurrencyIntegrationTest {
  private static final String TASK_CANCEL_PROPERTY = "norm.test.java.task.explicit-cancelled";
  private static final String UNMANAGED_FUTURE_PROPERTY = "norm.test.java.future.scope-cancelled";
  @TempDir Path temporaryDirectory;

  @Test
  void invokesJavaSamInterfacesWithNativeNormFunctions() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("callback/binding"));
    Path jar = callbackJar(moduleRoot.resolve("lib/callback.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "callback.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/callback.jar",
                integrity: sha256("%s")
              ),
              api: [
                jarType(
                  name: "CallbackApi",
                  members: ["consume", "customTransform", "remember", "same", "supply", "test", "transform"]
                )
              ]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package callback.binding
        import std.core.Exception

        Void main() {
          String prefix = "Norm"
          Function<String?()> supplier = () {
            String? value = prefix
            return value
          }
          Function<String?(String?)> suffix = (value) {
            String? result = (value ?? "") + "!"
            return result
          }
          Function<Void(String?)> consumer = (value) { printLine(value ?? "missing") }
          Function<Boolean(String?)> predicate = (value) { (value ?? "").codePointSize() == 3 }
          callbackApiRemember(supplier)
          require(condition: callbackApiSame(supplier), message: "callback identity changed between calls")
          Function<String?()> other = () { "other" }
          require(condition: !callbackApiSame(other), message: "distinct callbacks shared identity")
          printLine(callbackApiSupply(supplier) ?? "")
          printLine(callbackApiTransform(arg0: "NAR", arg1: suffix) ?? "")
          printLine(callbackApiCustomTransform(arg0: "custom", arg1: suffix) ?? "")
          callbackApiConsume(arg0: "seen", arg1: consumer)
          printLine(callbackApiTest(arg0: "NAR", arg1: predicate))
          Function<String?()> failure = () { throw Exception(message: "callback failure") }
          try {
            callbackApiSupply(failure)
          } catch Exception exception {
            printLine(exception.message)
          }
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("callback-cache"));
        ApplicationRunner launcher =
            new ApplicationRunner(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(
            System.lineSeparator(),
            "Norm",
            "NAR!",
            "custom!",
            "seen",
            "true",
            "callback failure",
            ""),
        output.toString());
  }

  @Test
  void bridgesNominalJavaFuturesExplicitlyThroughStandardNormConcurrency() throws Exception {
    System.clearProperty(TASK_CANCEL_PROPERTY);
    System.clearProperty(UNMANAGED_FUTURE_PROPERTY);
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("task/binding"));
    Path jar = taskJar(moduleRoot.resolve("lib/task.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "task.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/task.jar",
                integrity: sha256("%s")
              ),
              api: [
                jarType(
                  name: "TaskApi",
                  members: [
                    "asyncCheck",
                    "awaitStage",
                    "cancel",
                    "done",
                    "cancelled",
                    "completed",
                    "failed",
                    "pending",
                    "read",
                    "threadedCheck",
                    "threadName"
                  ]
                )
              ]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package task.binding
        import std.concurrent.startTask
        import std.concurrent.completion
        import std.core.Exception

        Void main() {
          var source = completion<String?>()
          var supplied = source.task()
          source.succeed("completed")
          require(condition: supplied.await() == "completed", message: "Norm completion preserves its own task contract")
          source.close()
          var empty = taskApiCompleted(null)!!
          require(condition: taskApiAwaitStage(empty) == null, message: "Java completion preserves null")
          var started = startTask<String?> { taskApiAwaitStage(taskApiCompleted("started")!!) }
          printLine(started.await() ?? "missing")
          started.close()
          String? owner = taskApiThreadName()
          Function<Boolean?()> onJavaCallbackThread = () {
            Boolean? result = taskApiThreadName() != owner
            return result
          }
          var asynchronous = taskApiAsyncCheck(onJavaCallbackThread)!!
          printLine(taskApiAwaitStage(asynchronous) ?? false)
          printLine(taskApiThreadedCheck(onJavaCallbackThread) ?? false)
          Function<Boolean?()> callbackFailure = () {
            throw Exception(message: "async callback failure")
          }
          var failedCallback = taskApiAsyncCheck(callbackFailure)!!
          try {
            taskApiAwaitStage(failedCallback)
          } catch Exception exception {
            printLine(exception.message)
          }
          var completed = taskApiCompleted("ready")!!
          printLine(taskApiAwaitStage(completed) ?? "missing")
          var failed = taskApiFailed("failure")!!
          try {
            taskApiRead(failed)
          } catch Exception exception {
            printLine(exception.message)
          }
          var pending = taskApiPending("norm.test.java.task.explicit-cancelled")!!
          printLine(taskApiDone(pending))
          require(condition: taskApiCancel(pending), message: "explicit Java cancellation succeeds")
          printLine(taskApiCancelled(pending))
          var unmanaged = taskApiPending("norm.test.java.future.scope-cancelled")!!
          require(condition: !taskApiDone(unmanaged), message: "nominal Java future remains pending")
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("task-cache"));
        ApplicationRunner launcher =
            new ApplicationRunner(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(
            System.lineSeparator(),
            "started",
            "true",
            "true",
            "async callback failure",
            "ready",
            "failure",
            "false",
            "true",
            ""),
        output.toString());
    assertEquals("true", System.getProperty(TASK_CANCEL_PROPERTY));
    assertNull(System.getProperty(UNMANAGED_FUTURE_PROPERTY));
  }

  private static Path callbackJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    String owner = "sample/CallbackApi";
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
    writer
        .visitField(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "remembered",
            "Ljava/util/function/Supplier;",
            null,
            null)
        .visitEnd();
    MethodVisitor remember =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "remember",
            "(Ljava/util/function/Supplier;)V",
            "(Ljava/util/function/Supplier<Ljava/lang/String;>;)V",
            null);
    remember.visitCode();
    remember.visitVarInsn(Opcodes.ALOAD, 0);
    remember.visitFieldInsn(
        Opcodes.PUTSTATIC, owner, "remembered", "Ljava/util/function/Supplier;");
    remember.visitInsn(Opcodes.RETURN);
    remember.visitMaxs(0, 0);
    remember.visitEnd();
    MethodVisitor same =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "same",
            "(Ljava/util/function/Supplier;)Z",
            "(Ljava/util/function/Supplier<Ljava/lang/String;>;)Z",
            null);
    same.visitCode();
    same.visitFieldInsn(Opcodes.GETSTATIC, owner, "remembered", "Ljava/util/function/Supplier;");
    same.visitVarInsn(Opcodes.ALOAD, 0);
    var different = new org.objectweb.asm.Label();
    same.visitJumpInsn(Opcodes.IF_ACMPNE, different);
    same.visitInsn(Opcodes.ICONST_1);
    same.visitInsn(Opcodes.IRETURN);
    same.visitLabel(different);
    same.visitInsn(Opcodes.ICONST_0);
    same.visitInsn(Opcodes.IRETURN);
    same.visitMaxs(0, 0);
    same.visitEnd();
    MethodVisitor supply =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "supply",
            "(Ljava/util/function/Supplier;)Ljava/lang/String;",
            "(Ljava/util/function/Supplier<Ljava/lang/String;>;)Ljava/lang/String;",
            null);
    supply.visitCode();
    supply.visitVarInsn(Opcodes.ALOAD, 0);
    supply.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "java/util/function/Supplier",
        "get",
        "()Ljava/lang/Object;",
        true);
    supply.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
    supply.visitInsn(Opcodes.ARETURN);
    supply.visitMaxs(0, 0);
    supply.visitEnd();
    MethodVisitor transform =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "transform",
            "(Ljava/lang/String;Ljava/util/function/Function;)Ljava/lang/String;",
            "(Ljava/lang/String;Ljava/util/function/Function<-Ljava/lang/String;+Ljava/lang/String;>;)Ljava/lang/String;",
            null);
    transform.visitCode();
    transform.visitVarInsn(Opcodes.ALOAD, 1);
    transform.visitVarInsn(Opcodes.ALOAD, 0);
    transform.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "java/util/function/Function",
        "apply",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        true);
    transform.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
    transform.visitInsn(Opcodes.ARETURN);
    transform.visitMaxs(0, 0);
    transform.visitEnd();
    MethodVisitor consume =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "consume",
            "(Ljava/lang/String;Ljava/util/function/Consumer;)V",
            "(Ljava/lang/String;Ljava/util/function/Consumer<-Ljava/lang/String;>;)V",
            null);
    consume.visitCode();
    consume.visitVarInsn(Opcodes.ALOAD, 1);
    consume.visitVarInsn(Opcodes.ALOAD, 0);
    consume.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "java/util/function/Consumer",
        "accept",
        "(Ljava/lang/Object;)V",
        true);
    consume.visitInsn(Opcodes.RETURN);
    consume.visitMaxs(0, 0);
    consume.visitEnd();
    MethodVisitor test =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "test",
            "(Ljava/lang/String;Ljava/util/function/Predicate;)Z",
            "(Ljava/lang/String;Ljava/util/function/Predicate<-Ljava/lang/String;>;)Z",
            null);
    test.visitCode();
    test.visitVarInsn(Opcodes.ALOAD, 1);
    test.visitVarInsn(Opcodes.ALOAD, 0);
    test.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "java/util/function/Predicate",
        "test",
        "(Ljava/lang/Object;)Z",
        true);
    test.visitInsn(Opcodes.IRETURN);
    test.visitMaxs(0, 0);
    test.visitEnd();
    MethodVisitor customTransform =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "customTransform",
            "(Ljava/lang/String;Lsample/Mapper;)Ljava/lang/String;",
            "(Ljava/lang/String;Lsample/Mapper<-Ljava/lang/String;+Ljava/lang/String;>;)Ljava/lang/String;",
            null);
    customTransform.visitCode();
    customTransform.visitVarInsn(Opcodes.ALOAD, 1);
    customTransform.visitVarInsn(Opcodes.ALOAD, 0);
    customTransform.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "sample/Mapper",
        "map",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        true);
    customTransform.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
    customTransform.visitInsn(Opcodes.ARETURN);
    customTransform.visitMaxs(0, 0);
    customTransform.visitEnd();
    writer.visitEnd();
    ClassWriter mapper = new ClassWriter(0);
    mapper.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
        "sample/Mapper",
        "<T:Ljava/lang/Object;R:Ljava/lang/Object;>Ljava/lang/Object;",
        "java/lang/Object",
        null);
    mapper
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "map",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            "(TT;)TR;",
            null)
        .visitEnd();
    mapper.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(path))) {
      archive.putNextEntry(new JarEntry("sample/CallbackApi.class"));
      archive.write(writer.toByteArray());
      archive.closeEntry();
      archive.putNextEntry(new JarEntry("sample/Mapper.class"));
      archive.write(mapper.toByteArray());
      archive.closeEntry();
    }
    return path;
  }

  private static Path taskJar(Path path) throws Exception {
    Path classes = Files.createDirectories(path.getParent().resolve("task-classes"));
    Path source = path.getParent().resolve("TaskApi.java");
    Files.writeString(
        source,
        """
        package sample;
        import java.util.concurrent.*;
        import java.util.function.Supplier;
        public final class TaskApi {
          public static String threadName() { return Thread.currentThread().getName(); }
          public static CompletionStage<Boolean> asyncCheck(Supplier<Boolean> callback) {
            return CompletableFuture.supplyAsync(callback);
          }
          public static Boolean threadedCheck(Supplier<Boolean> callback) {
            return CompletableFuture.supplyAsync(callback).join();
          }
          public static CompletionStage<String> completed(String value) {
            return CompletableFuture.completedFuture(value).minimalCompletionStage();
          }
          public static CompletableFuture<String> failed(String message) {
            return CompletableFuture.failedFuture(new IllegalStateException(message));
          }
          public static CompletableFuture<String> pending(String property) {
            return new CompletableFuture<String>() {
              @Override public boolean cancel(boolean interrupt) {
                System.setProperty(property, "true");
                return super.cancel(interrupt);
              }
            };
          }
          public static <T> T awaitStage(CompletionStage<T> stage) {
            try { return stage.toCompletableFuture().join(); }
            catch (CompletionException failure) {
              if (failure.getCause() instanceof RuntimeException cause) throw cause;
              throw failure;
            }
          }
          public static <T> T read(Future<T> future) throws Exception {
            try { return future.get(); }
            catch (ExecutionException failure) {
              if (failure.getCause() instanceof RuntimeException cause) throw cause;
              throw failure;
            }
          }
          public static boolean done(Future<?> future) { return future.isDone(); }
          public static boolean cancelled(Future<?> future) { return future.isCancelled(); }
          public static boolean cancel(Future<?> future) { return future.cancel(true); }
        }
        """);
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), source.toString()));
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path));
        var files = Files.walk(classes)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
        Files.copy(file, output);
        output.closeEntry();
      }
    }
    return path;
  }
}
