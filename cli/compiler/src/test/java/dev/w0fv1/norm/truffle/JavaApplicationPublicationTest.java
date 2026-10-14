package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.core.CoreLiveSchema;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ExecutionRevision;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class JavaApplicationPublicationTest {
  @Test
  void candidateCallbackWaitsForTheSameRealmPublicationBoundary() throws Exception {
    try (var compiler = new CompilerSession();
        var runtime = JvmJarBindingRuntime.prepared(List.of(), List.of(), List.of())) {
      var before = compiler.compile(SourceFile.of(Path.of("publication.norm"), "Void main() {}"));
      var after =
          compiler.compile(
              SourceFile.of(Path.of("publication.norm"), "Void main() { var value = 2 }"));
      assertTrue(before.isSuccess(), before.diagnostics().toString());
      assertTrue(after.isSuccess(), after.diagnostics().toString());
      var initialArtifact = before.output().orElseThrow().artifact();
      var candidateArtifact = after.output().orElseThrow().artifact();
      var context = ExecutionContext.builder().jarBindingRuntime(runtime).build();
      var initialLowerer = new Lowerer(null, CoreLiveSchema.create(initialArtifact));
      var initialProgram = initialLowerer.lower(initialArtifact);
      var initial = initialLowerer.liveVersion();
      initial.attach(ExecutionRevision.unmanaged(initialArtifact, context));
      initial.attachRuntime(initialProgram);
      var candidateLowerer = new Lowerer(null, CoreLiveSchema.create(candidateArtifact));
      var candidateProgram = candidateLowerer.lower(candidateArtifact);
      var candidate = candidateLowerer.liveVersion();
      candidate.attach(ExecutionRevision.unmanaged(candidateArtifact, context));
      candidate.attachRuntime(candidateProgram);
      try (var bindings = new LiveBindings(initial)) {
        var execution = initialProgram.execution(context, bindings);
        var attempted = new CountDownLatch(1);
        var completed = new CountDownLatch(1);
        var callback = new AtomicReference<Thread>();
        var failure = new AtomicReference<Throwable>();
        bindings.beginExecution(execution);
        try {
          execution
              .callbacks()
              .hostCall(
                  () -> {
                    synchronized (bindings) {
                      candidate.installBridge(execution);
                      callback.set(
                          Thread.ofVirtual()
                              .start(
                                  () -> {
                                    attempted.countDown();
                                    try {
                                      assertEquals(
                                          "candidate",
                                          JavaApplicationBridge.toJava(
                                              runtime.applicationClassLoader(), "candidate"));
                                    } catch (Throwable exception) {
                                      failure.set(exception);
                                    } finally {
                                      completed.countDown();
                                    }
                                  }));
                      assertTrue(attempted.await(5, TimeUnit.SECONDS));
                      assertFalse(
                          completed.await(250, TimeUnit.MILLISECONDS),
                          "candidate entered before publication");
                      bindings.publish(candidate);
                    }
                    assertTrue(completed.await(5, TimeUnit.SECONDS));
                    return null;
                  });
          if (failure.get() != null) throw new AssertionError(failure.get());
        } finally {
          bindings.endExecution();
          execution.close(null);
          if (callback.get() != null) callback.get().join(5000);
          candidate.revision().close();
        }
      }
    }
  }
}
