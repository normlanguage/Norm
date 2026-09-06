package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.w0fv1.norm.core.*;
import dev.w0fv1.norm.testing.NormTestKit;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuestValueFactoryTest {
  @Test
  void preservesGuestExceptionIdentityAcrossJavaRoundTrip() {
    var artifact =
        NormTestKit.compile(
                """
        import std.core.Exception
        class Failure extends Exception {
          Failure(String message) { super(message: message) }
        }
        Void main() { throw Failure("boundary failure") }
        """)
            .program()
            .orElseThrow()
            .compilation()
            .artifact();
    var executable = new TruffleExecutionBackend().compile(null, artifact);
    var context =
        dev.w0fv1.norm.execution.ExecutionContext.of(
            new java.io.PrintWriter(new java.io.StringWriter()),
            dev.w0fv1.norm.platform.jdk.JdkSystemPlatform.standard());
    var execution = executable.execution(context);
    try {
      var thrown =
          org.junit.jupiter.api.Assertions.assertThrows(
              NormThrownException.class, () -> executable.entryPoint().call(execution));
      var host =
          org.junit.jupiter.api.Assertions.assertInstanceOf(
              RuntimeException.class, executable.values().javaArgument(thrown.value));
      assertSame(thrown.value, executable.values().javaExceptionValue(host, execution));
      assertSame(host, executable.values().javaArgument(thrown.value));
    } finally {
      execution.close(null);
    }
  }

  @Test
  void ordinaryJavaArgumentsDoNotRequireUnrelatedStandardTypes() {
    var compiled = NormTestKit.compile("class Entity {} Void main() { Entity value = Entity() }");
    var artifact =
        CoreReachability.retainApplication(
            compiled.program().orElseThrow().compilation().artifact());
    assertFalse(
        artifact.namespace().bindings().stream()
            .anyMatch(
                binding ->
                    binding.packageName().equals("std.time") && binding.name().equals("Duration")));
    var definition =
        artifact.program().definitions().stream()
            .filter(
                record ->
                    record.definition() instanceof CoreDefinition.Aggregate aggregate
                        && aggregate.nominalType().name().equals("Entity"))
            .findFirst()
            .orElseThrow();
    var aggregate = (CoreDefinition.Aggregate) definition.definition();
    var type =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(definition.id())),
            List.of(),
            aggregate.valueCategory(),
            CoreNullability.NON_NULL);
    var executable = new TruffleExecutionBackend().compile(null, artifact);
    var value = executable.values().allocate(type);
    assertSame(value, executable.values().javaArgument(value));
  }
}
