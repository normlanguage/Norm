package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreLiveSchema;
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
final class JavaStableTargetExecutionTest {
  @Test
  void sameContentDeclarationsKeepDistinctJavaExecutionBindings() {
    String body =
        "var count = 0; for (count < 3) { printLine(\"old:\" + count.toString()); count = count + 1 }";
    String source = "Void first() { " + body + " } Void second() { " + body + " } Void main() {}";
    var originalArtifact = NormTestKit.compile(source).output().orElseThrow().artifact();
    var candidateArtifact =
        NormTestKit.compile(
                source.replace(
                    "Void second() { " + body + " }",
                    "Void second() { " + body.replace("old:", "new:") + " }"))
            .output()
            .orElseThrow()
            .artifact();
    var schema = CoreLiveSchema.create(originalArtifact);
    var candidateSchema = CoreLiveSchema.create(candidateArtifact);
    schema.requireCompatible(candidateSchema);
    var first =
        originalArtifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("first"))
            .findFirst()
            .orElseThrow()
            .occurrence();
    var second =
        originalArtifact.namespace().bindings().stream()
            .filter(binding -> binding.name().equals("second"))
            .findFirst()
            .orElseThrow()
            .occurrence();
    assertEquals(first.representative(), second.representative());
    var lowerer = new Lowerer(null, schema);
    var program =
        lowerer.lower(originalArtifact, CoreExecutionPlan.forLiveArtifact(originalArtifact));
    var original = lowerer.liveVersion();
    original.attachRuntime(program);
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
        var execution = program.execution(context, bindings);
        bindings.beginExecution(execution);
        try {
          var dispatch =
              new JavaApplicationDispatch(
                  program.program(),
                  program.bridgeTargets(),
                  program.values(),
                  execution,
                  runtime,
                  original);
          dispatch.invoke(
              program.program().identity().slot(second).toString(), null, new Object[0]);
          assertEquals(List.of("old:0", "new:1", "new:2"), lines);
        } finally {
          execution.close(null);
          bindings.endExecution();
        }
      }
    }
  }
}
