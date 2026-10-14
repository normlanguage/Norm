package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreLiveSchema;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ExecutionRevision;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
final class JavaLiveCallbackExecutionTest {
  @Test
  void runningJavaCallbackAdoptsNewLoopCodeAndPreservesItsFrame() {
    String source =
        """
        Void loop() {
          var count = 0
          for (count < 3) { printLine("old:" + count.toString()); count = count + 1 }
        }
        Void main() {}
        """;
    var originalResult = NormTestKit.compile(source);
    var candidateResult =
        NormTestKit.compile(
            source.replace("old:", "new:").replace("var count = 0", "var count = 999"));
    assertTrue(originalResult.isSuccess(), originalResult.diagnostics().toString());
    assertTrue(candidateResult.isSuccess(), candidateResult.diagnostics().toString());
    var originalArtifact = originalResult.output().orElseThrow().artifact();
    var candidateArtifact = candidateResult.output().orElseThrow().artifact();
    var originalSchema = CoreLiveSchema.create(originalArtifact);
    var candidateSchema = CoreLiveSchema.create(candidateArtifact);
    originalSchema.requireCompatible(candidateSchema);
    var originalLowerer = new Lowerer(null, originalSchema);
    var originalProgram =
        originalLowerer.lower(
            originalArtifact, CoreExecutionPlan.forLiveArtifact(originalArtifact));
    var original = originalLowerer.liveVersion();
    original.attachRuntime(originalProgram);
    var candidateLowerer = new Lowerer(null, candidateSchema);
    var candidateProgram =
        candidateLowerer.lower(
            candidateArtifact, CoreExecutionPlan.forLiveArtifact(candidateArtifact));
    var candidate = candidateLowerer.liveVersion();
    candidate.attachRuntime(candidateProgram);
    var bindingsReference = new AtomicReference<LiveBindings>();
    var lines = new ArrayList<String>();
    var pending = new StringBuilder();
    var writer =
        new Writer() {
          @Override
          public void write(char[] characters, int offset, int length) {
            for (int index = offset; index < offset + length; index++) {
              char character = characters[index];
              if (character == '\n') {
                lines.add(pending.toString());
                pending.setLength(0);
                if (lines.size() == 1) bindingsReference.get().publish(candidate);
              } else if (character != '\r') pending.append(character);
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    try (var runtime = JvmJarBindingRuntime.prepared(List.of(), List.of(), List.of())) {
      var context =
          ExecutionContext.builder()
              .output(new PrintWriter(writer))
              .jarBindingRuntime(runtime)
              .build();
      original.attach(ExecutionRevision.unmanaged(originalArtifact, context));
      candidate.attach(ExecutionRevision.unmanaged(candidateArtifact, context));
      try (var bindings = new LiveBindings(original)) {
        bindingsReference.set(bindings);
        var execution = originalProgram.execution(context, bindings);
        bindings.beginExecution(execution);
        try {
          var declarations =
              originalArtifact.namespace().bindings().stream()
                  .filter(binding -> binding.name().equals("loop"))
                  .map(binding -> binding.occurrence())
                  .distinct()
                  .toList();
          assertEquals(1, declarations.size());
          var declaration = declarations.getFirst();
          var closure =
              new RuntimeValues.Closure(
                  originalProgram
                      .bridgeTargets()
                      .get(
                          dev.w0fv1.norm.core.CoreRuntimeIdentity.create(originalArtifact)
                              .slot(declaration)),
                  declaration,
                  null,
                  false,
                  null,
                  new Object[0],
                  new Object[0],
                  new Object[0],
                  new CoreType.Function(CoreType.VOID, List.of(), CoreNullability.NON_NULL),
                  originalProgram.annotations());
          var callback =
              assertInstanceOf(
                  Runnable.class,
                  JavaApplicationBridge.toJava(runtime.applicationClassLoader(), closure));
          callback.run();
          assertEquals(List.of("old:0", "new:1", "new:2"), lines);
        } finally {
          execution.close(null);
          bindings.endExecution();
        }
      }
    }
  }
}
