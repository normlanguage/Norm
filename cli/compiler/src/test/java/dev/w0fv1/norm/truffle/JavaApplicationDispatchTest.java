package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

final class JavaApplicationDispatchTest {
  @Test
  void ownsNewHostResourcesAndKeepsOneHandleAcrossApplicationCalls() throws Exception {
    var artifact =
        NormTestKit.compile(
                """
        import java.base.io.InputStream
        Any? relay(Any? bridgeAny) { return bridgeAny }
        Integer read(InputStream bridgeInput) { return bridgeInput.read() }
        Void close(InputStream bridgeClose) { bridgeClose.close() }
        Void main() {}
        """)
            .output()
            .orElseThrow()
            .artifact();
    var methods =
        artifact.program().definitions().stream()
            .filter(
                record ->
                    record.definition() instanceof CoreDefinition.Callable callable
                        && callable.parameters().size() == 1
                        && callable.parameters().getFirst().name().startsWith("bridge"))
            .collect(
                java.util.stream.Collectors.toMap(
                    record ->
                        ((CoreDefinition.Callable) record.definition())
                            .parameters()
                            .getFirst()
                            .name(),
                    dev.w0fv1.norm.core.CoreDefinitionRecord::id));
    var executable =
        new TruffleExecutionBackend()
            .compile(
                null,
                artifact,
                dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(
                    artifact, new java.util.HashSet<>(methods.values())));
    var closes = new java.util.concurrent.atomic.AtomicInteger();
    var input =
        new java.io.ByteArrayInputStream(new byte[] {65}) {
          @Override
          public void close() {
            closes.incrementAndGet();
          }
        };
    try (var environment =
            dev.w0fv1.norm.project.ProjectEnvironment.bootstrap(
                new dev.w0fv1.norm.runtime.NormRuntime());
        var runtime = new JvmJarBindingRuntime(environment.javaBindings())) {
      var execution =
          executable.execution(
              ExecutionContext.of(new PrintWriter(new StringWriter()))
                  .withJarBindingRuntime(runtime));
      try {
        var dispatch =
            new JavaApplicationDispatch(
                executable.program(),
                executable.targets(),
                executable.values(),
                execution,
                runtime);
        try (var bridge =
            dev.w0fv1.norm.bridge.JavaApplicationBridge.install(
                runtime.applicationClassLoader(), dispatch)) {
          org.junit.jupiter.api.Assertions.assertSame(
              input,
              dispatch.invoke(methods.get("bridgeAny").toString(), null, new Object[] {input}));
          assertEquals(
              65,
              dispatch.invoke(methods.get("bridgeInput").toString(), null, new Object[] {input}));
          dispatch.invoke(methods.get("bridgeClose").toString(), null, new Object[] {input});
          assertEquals(1, closes.get());
        }
      } finally {
        execution.close(null);
      }
    }
    assertEquals(1, closes.get());
  }

  @Test
  void preservesNativeTaskOwnershipWhenItsHandleReturnsThroughTheApplicationBridge() {
    var artifact =
        NormTestKit.compile(
                """
        import std.concurrent.Task
        import std.concurrent.startTask
        Task<Integer> produce(Integer bridgeSeed) { return startTask { bridgeSeed } }
        Task<Integer> relay(Task<Integer> bridgeTask) { return bridgeTask }
        Integer read(Task<Integer> bridgeAwait) { return bridgeAwait.await() }
        Void main() { printLine(read(relay(produce(7)))) }
        """)
            .output()
            .orElseThrow()
            .artifact();
    var executable = new TruffleExecutionBackend().compile(null, artifact);
    var execution = executable.execution(ExecutionContext.of(new PrintWriter(new StringWriter())));
    try (var runtime = new JvmJarBindingRuntime(List.of())) {
      var dispatch =
          new JavaApplicationDispatch(
              executable.program(), executable.targets(), executable.values(), execution, runtime);
      var methods =
          artifact.program().definitions().stream()
              .filter(
                  record ->
                      record.definition() instanceof CoreDefinition.Callable callable
                          && callable.parameters().size() == 1
                          && callable.parameters().getFirst().name().startsWith("bridge"))
              .collect(
                  java.util.stream.Collectors.toMap(
                      record ->
                          ((CoreDefinition.Callable) record.definition())
                              .parameters()
                              .getFirst()
                              .name(),
                      record -> record.id().toString()));
      var task = dispatch.invoke(methods.get("bridgeSeed"), null, new Object[] {7});
      var returned = dispatch.invoke(methods.get("bridgeTask"), null, new Object[] {task});
      org.junit.jupiter.api.Assertions.assertSame(task, returned);
      assertEquals(7, dispatch.invoke(methods.get("bridgeAwait"), null, new Object[] {returned}));
    } finally {
      execution.close(null);
    }
  }

  @Test
  void preservesCodePointRepresentationWhenJavaCallsNormAndReceivesItsResult() {
    var artifact =
        NormTestKit.compile(
                "CodePoint echo(CodePoint bridgeCharacter) { printLine(bridgeCharacter.scalarValue()) return bridgeCharacter } "
                    + "Void main() { echo('A') }")
            .output()
            .orElseThrow()
            .artifact();
    var target =
        artifact.program().definitions().stream()
            .filter(
                record ->
                    record.definition() instanceof CoreDefinition.Callable callable
                        && callable.returnType().equals(CoreType.CODE_POINT)
                        && callable.parameters().size() == 1
                        && callable.parameters().getFirst().name().equals("bridgeCharacter"))
            .findFirst()
            .orElseThrow()
            .id();
    var executable = new TruffleExecutionBackend().compile(null, artifact);
    var output = new StringWriter();
    var execution = executable.execution(ExecutionContext.of(new PrintWriter(output)));
    try (var runtime = new JvmJarBindingRuntime(List.of())) {
      var dispatch =
          new JavaApplicationDispatch(
              executable.program(), executable.targets(), executable.values(), execution, runtime);
      assertEquals(0x1F600, dispatch.invoke(target.toString(), null, new Object[] {0x1F600}));
      assertEquals(65, dispatch.invoke(target.toString(), null, new Object[] {'A'}));
      assertEquals(
          "128512" + System.lineSeparator() + "65" + System.lineSeparator(), output.toString());
    } finally {
      execution.close(null);
    }
  }
}
