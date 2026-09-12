package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.SourceFormatter;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BlockCallChainExecutionTest {
  private static final String BOX =
      """
      class Box<T> {
        T value
        Box<R> map<R>(R transform(T result)) { Box<R>(value: transform(value)) }
      }
      Box<T> produce<T>(T work()) { Box<T>(value: work()) }
      """;

  @Test
  void executesGenericSynchronousStagesWithIndependentCallbackParameters() {
    String text =
        BOX
            + """
        Void main() {
          var answer = produce { 21 } map { result * 2 } map { item in item + 1 }
          printLine(answer.value)
          printLine(produce<Integer> { 2 } map { result.toString() }.value)
        }
        """;
    NormTestKit.assertOutput(text, "43", "2");
    assertEquals(NormTestKit.run(text.replace("} map", "}.map")), NormTestKit.run(text));
    String formatted =
        new SourceFormatter().format(SourceFile.of(Path.of("chain.norm"), text)).orElseThrow();
    assertEquals(NormTestKit.run(text), NormTestKit.run(formatted));
  }

  @Test
  void evaluatesTheReceiverOnlyOnceAndSharesOrdinaryExtensionResolution() {
    String text =
        BOX
            + """
        class Producer {
          Integer calls = 0
          Box<Integer> load(Integer work()) { calls = calls + 1; Box<Integer>(value: work()) }
        }
        extension Integer finish<T>(Box<T> box, Integer action(T result)) { action(box.value) }
        Void main() {
          var producer = Producer()
          var answer = producer.load { 21 } map { result * 2 } finish { result + 1 }
          printLine(answer)
          printLine(producer.calls)
        }
        """;
    NormTestKit.assertOutput(text, "43", "1");
  }

  @Test
  void neverFallsBackToATopLevelFunctionAndPreservesDiagnosticKinds() {
    String declarations = BOX + "Integer missing(Integer work()) { work() } ";
    var chained = NormTestKit.compile(declarations + "Void main() { produce { 1 } missing { 2 } }");
    var explicit =
        NormTestKit.compile(declarations + "Void main() { produce { 1 }.missing { 2 } }");
    assertFalse(chained.isSuccess());
    assertEquals(
        explicit.diagnostics().stream().map(d -> d.code()).toList(),
        chained.diagnostics().stream().map(d -> d.code()).toList());
    assertTrue(
        chained.diagnostics().stream().anyMatch(d -> d.primarySpan().text().contains("missing")));
    NormTestKit.assertOutput(
        declarations + "Void main() { produce { 1 }; printLine(missing { 2 }) }", "2");
  }

  @Test
  void doesNotAddImplicitNullSafety() {
    String declarations = BOX + "Box<Integer>? optional(Integer work()) { null } ";
    var chained =
        NormTestKit.compile(declarations + "Void main() { optional { 1 } map { result + 1 } }");
    var explicit =
        NormTestKit.compile(declarations + "Void main() { optional { 1 }.map { result + 1 } }");
    assertFalse(chained.isSuccess());
    assertEquals(
        explicit.diagnostics().stream().map(d -> d.code()).toList(),
        chained.diagnostics().stream().map(d -> d.code()).toList());
  }

  @Test
  void preservesTaskTransformationAndFailureRecovery() {
    NormTestKit.assertOutput(
        """
        import std.concurrent.async
        import std.concurrent.startTask
        import std.concurrent.Task
        import std.concurrent.TaskScope
        import std.context.withContext
        import std.core.Exception
        class WorkerScope implements TaskScope {
          Task<T> start<T>(Function<T()> work) { startTask<T>(work) }
        }
        Void main() {
          withContext<TaskScope>(value: WorkerScope(), action: () {
            var answer = async { 21 } then { result * 2 } then { item in item + 1 }
            printLine(answer.await())
            var recovered = async<Integer> { throw Exception(message: "expected") } error {
              require(condition: failure.message == "expected", message: "failure scope")
              7
            }
            printLine(recovered.await())
          })
        }
        """,
        "43",
        "7");
  }

  @Test
  void matchesColdCompilationAndExecutionAcrossNewlinesDotsAndUndo() {
    String declarations =
        """
        class Chain { Integer finish(Integer work()) { work() + 100 } }
        Chain produce(Integer work()) { work(); Chain() }
        Integer finish(Integer work()) { work() + 200 }
        """;
    var runtime = new NormRuntime(new TruffleExecutionBackend(4));
    try (CompilerSession hot = new CompilerSession()) {
      for (String connection :
          List.of(
              "} finish {",
              "}\nfinish {",
              "} finish\n{",
              "}\r\nfinish {",
              "}\rfinish {",
              "}.finish {",
              "}\n.finish {",
              "} finish {",
              "}\nfinish {")) {
        String text =
            declarations
                + "Integer run() { produce { 1 "
                + connection
                + " 2 } } Void main() { printLine(run()) }";
        SourceFile source = SourceFile.of(Path.of("incremental-chain.norm"), text);
        try (CompilerSession cold = new CompilerSession()) {
          var incremental = hot.compile(source);
          var fresh = cold.compile(source);
          assertTrue(incremental.isSuccess(), () -> incremental.diagnostics().toString());
          assertTrue(fresh.isSuccess(), () -> fresh.diagnostics().toString());
          assertEquals(
              fresh.output().orElseThrow().artifact().entryDefinition(),
              incremental.output().orElseThrow().artifact().entryDefinition());
          StringWriter output = new StringWriter();
          runtime.run(
              incremental.output().orElseThrow().artifact(),
              ExecutionContext.of(new PrintWriter(output), JdkSystemPlatform.standard()));
          String expected =
              connection.contains(".") || connection.equals("} finish {") ? "102" : "202";
          assertEquals(expected + System.lineSeparator(), output.toString());
        }
      }
    }
  }
}
