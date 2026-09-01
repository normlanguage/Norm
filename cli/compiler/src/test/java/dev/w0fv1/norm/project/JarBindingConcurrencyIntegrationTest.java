package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class JarBindingConcurrencyIntegrationTest {
  private static final String TASK_SCOPE_CANCEL_PROPERTY = "norm.test.java.task.scope-cancelled";
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
                  members: ["consume", "customTransform", "supply", "test", "transform"]
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
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
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
  void awaitsAndCancelsJavaTasksThroughStandardNormConcurrency() throws Exception {
    System.clearProperty(TASK_SCOPE_CANCEL_PROPERTY);
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
                    "cancelled",
                    "completed",
                    "failed",
                    "pending",
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
        import std.concurrent.Task
        import std.core.Exception

        Void main() {
          String? owner = taskApiThreadName()
          Function<Boolean?()> onOwner = () {
            Boolean? result = taskApiThreadName() == owner
            return result
          }
          Task<Boolean?>? asynchronous = taskApiAsyncCheck(onOwner)
          if asynchronous != null {
            printLine(asynchronous.await() ?? false)
            asynchronous.close()
          }
          printLine(taskApiThreadedCheck(onOwner) ?? false)
          Function<Boolean?()> callbackFailure = () {
            throw Exception(message: "async callback failure")
          }
          Task<Boolean?>? failedCallback = taskApiAsyncCheck(callbackFailure)
          if failedCallback != null {
            try {
              failedCallback.await()
            } catch Exception exception {
              printLine(exception.message)
            }
            failedCallback.close()
          }
          Task<String?>? completed = taskApiCompleted("ready")
          if completed != null {
            printLine(completed.completed())
            printLine(completed.await() ?? "missing")
            completed.close()
          }
          Task<String?>? failed = taskApiFailed("failure")
          if failed != null {
            try {
              failed.await()
            } catch Exception exception {
              printLine(exception.message)
            }
            failed.close()
          }
          Task<String?>? pending = taskApiPending("norm.test.java.task.explicit-cancelled")
          if pending != null {
            printLine(pending.completed())
            pending.close()
            printLine(taskApiCancelled(pending))
          }
          Task<String?>? automatic = taskApiPending("norm.test.java.task.scope-cancelled")
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("task-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(
            System.lineSeparator(),
            "true",
            "true",
            "async callback failure",
            "true",
            "ready",
            "failure",
            "false",
            "true",
            ""),
        output.toString());
    assertEquals("true", System.getProperty(TASK_SCOPE_CANCEL_PROPERTY));
  }

  private static Path callbackJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    String owner = "sample/CallbackApi";
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
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
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    String owner = "sample/TaskApi";
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
    MethodVisitor threadName =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "threadName",
            "()Ljava/lang/String;",
            null,
            null);
    threadName.visitCode();
    threadName.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
    threadName.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getName", "()Ljava/lang/String;", false);
    threadName.visitInsn(Opcodes.ARETURN);
    threadName.visitMaxs(0, 0);
    threadName.visitEnd();
    MethodVisitor asyncCheck =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "asyncCheck",
            "(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletionStage;",
            "(Ljava/util/function/Supplier<Ljava/lang/Boolean;>;)Ljava/util/concurrent/CompletionStage<Ljava/lang/Boolean;>;",
            null);
    asyncCheck.visitCode();
    asyncCheck.visitVarInsn(Opcodes.ALOAD, 0);
    asyncCheck.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/util/concurrent/CompletableFuture",
        "supplyAsync",
        "(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
        false);
    asyncCheck.visitInsn(Opcodes.ARETURN);
    asyncCheck.visitMaxs(0, 0);
    asyncCheck.visitEnd();
    MethodVisitor threadedCheck =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "threadedCheck",
            "(Ljava/util/function/Supplier;)Ljava/lang/Boolean;",
            "(Ljava/util/function/Supplier<Ljava/lang/Boolean;>;)Ljava/lang/Boolean;",
            null);
    threadedCheck.visitCode();
    threadedCheck.visitVarInsn(Opcodes.ALOAD, 0);
    threadedCheck.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/util/concurrent/CompletableFuture",
        "supplyAsync",
        "(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
        false);
    threadedCheck.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/util/concurrent/CompletableFuture",
        "join",
        "()Ljava/lang/Object;",
        false);
    threadedCheck.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
    threadedCheck.visitInsn(Opcodes.ARETURN);
    threadedCheck.visitMaxs(0, 0);
    threadedCheck.visitEnd();
    MethodVisitor completed =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "completed",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletionStage;",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletionStage<Ljava/lang/String;>;",
            null);
    completed.visitCode();
    completed.visitVarInsn(Opcodes.ALOAD, 0);
    completed.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/util/concurrent/CompletableFuture",
        "completedFuture",
        "(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;",
        false);
    completed.visitInsn(Opcodes.ARETURN);
    completed.visitMaxs(0, 0);
    completed.visitEnd();
    MethodVisitor failed =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "failed",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture<Ljava/lang/String;>;",
            null);
    failed.visitCode();
    failed.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
    failed.visitInsn(Opcodes.DUP);
    failed.visitVarInsn(Opcodes.ALOAD, 0);
    failed.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "java/lang/IllegalStateException",
        "<init>",
        "(Ljava/lang/String;)V",
        false);
    failed.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/util/concurrent/CompletableFuture",
        "failedFuture",
        "(Ljava/lang/Throwable;)Ljava/util/concurrent/CompletableFuture;",
        false);
    failed.visitInsn(Opcodes.ARETURN);
    failed.visitMaxs(0, 0);
    failed.visitEnd();
    MethodVisitor pending =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "pending",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture<Ljava/lang/String;>;",
            null);
    pending.visitCode();
    pending.visitTypeInsn(Opcodes.NEW, "sample/TrackingFuture");
    pending.visitInsn(Opcodes.DUP);
    pending.visitVarInsn(Opcodes.ALOAD, 0);
    pending.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "sample/TrackingFuture", "<init>", "(Ljava/lang/String;)V", false);
    pending.visitInsn(Opcodes.ARETURN);
    pending.visitMaxs(0, 0);
    pending.visitEnd();
    MethodVisitor cancelled =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "cancelled",
            "(Ljava/util/concurrent/Future;)Z",
            "(Ljava/util/concurrent/Future<Ljava/lang/String;>;)Z",
            null);
    cancelled.visitCode();
    cancelled.visitVarInsn(Opcodes.ALOAD, 0);
    cancelled.visitMethodInsn(
        Opcodes.INVOKEINTERFACE, "java/util/concurrent/Future", "isCancelled", "()Z", true);
    cancelled.visitInsn(Opcodes.IRETURN);
    cancelled.visitMaxs(0, 0);
    cancelled.visitEnd();
    writer.visitEnd();
    ClassWriter tracking = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    tracking.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/TrackingFuture",
        "Ljava/util/concurrent/CompletableFuture<Ljava/lang/String;>;",
        "java/util/concurrent/CompletableFuture",
        null);
    tracking
        .visitField(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "key", "Ljava/lang/String;", null, null)
        .visitEnd();
    MethodVisitor constructor =
        tracking.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/util/concurrent/CompletableFuture", "<init>", "()V", false);
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitVarInsn(Opcodes.ALOAD, 1);
    constructor.visitFieldInsn(
        Opcodes.PUTFIELD, "sample/TrackingFuture", "key", "Ljava/lang/String;");
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(0, 0);
    constructor.visitEnd();
    MethodVisitor cancel = tracking.visitMethod(Opcodes.ACC_PUBLIC, "cancel", "(Z)Z", null, null);
    cancel.visitCode();
    cancel.visitVarInsn(Opcodes.ALOAD, 0);
    cancel.visitFieldInsn(Opcodes.GETFIELD, "sample/TrackingFuture", "key", "Ljava/lang/String;");
    cancel.visitLdcInsn("true");
    cancel.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/System",
        "setProperty",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        false);
    cancel.visitInsn(Opcodes.POP);
    cancel.visitVarInsn(Opcodes.ALOAD, 0);
    cancel.visitVarInsn(Opcodes.ILOAD, 1);
    cancel.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/util/concurrent/CompletableFuture", "cancel", "(Z)Z", false);
    cancel.visitInsn(Opcodes.IRETURN);
    cancel.visitMaxs(0, 0);
    cancel.visitEnd();
    tracking.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/TaskApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
      output.putNextEntry(new JarEntry("sample/TrackingFuture.class"));
      output.write(tracking.toByteArray());
      output.closeEntry();
    }
    return path;
  }
}
