package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class ConstructorInterceptorExecutionTest {
  @Test
  void preservesGenericConstructorTypesThroughFunctionInterceptorHooks() {
    var result =
        NormTestKit.compile(
            """
        import std.annotation.FunctionInterceptor
        import std.annotation.ConstructorTarget
        import std.annotation.RuntimeRetention
        annotation Trace implements FunctionInterceptor, ConstructorTarget, RuntimeRetention {
          Void before(FunctionContext context) { printLine("before") }
          Void after(FunctionContext context, FunctionCompletion completion) { printLine("after") }
        }
        class Box<T> { T value @Trace Box(T value) { this.value = value } }
        Void main() { printLine(Box<Integer>(19).value) }
        """);
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var output = new StringWriter();
    new NormRuntime().run(result.output().orElseThrow().artifact(), new PrintWriter(output));
    assertEquals(java.util.List.of("before", "after", "19"), output.toString().lines().toList());
  }

  @Test
  void transformsProvidedConstructorParametersThroughTheirInterceptors() {
    var result =
        NormTestKit.compile(
            """
        import std.annotation.ParameterInterceptor
        import std.annotation.RuntimeRetention
        annotation Increment implements ParameterInterceptor<Integer>, RuntimeRetention {
          Integer before(ParameterContext context, Integer value) { printLine("parameter:" + value.toString()); value + 1 }
          Void after(ParameterContext context, FunctionCompletion completion) { printLine("parameter-after") }
        }
        class Box { Integer value Box(@Increment Integer value) { this.value = value } }
        Void main() { printLine(Box(19).value) }
        """);
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    var output = new StringWriter();
    new NormRuntime().run(result.output().orElseThrow().artifact(), new PrintWriter(output));
    assertEquals(
        java.util.List.of("parameter:19", "parameter-after", "20"),
        output.toString().lines().toList());
  }
}
