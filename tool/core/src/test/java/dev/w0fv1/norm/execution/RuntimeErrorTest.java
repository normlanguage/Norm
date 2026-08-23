package dev.w0fv1.norm.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class RuntimeErrorTest {
  @TestFactory
  Stream<DynamicTest> reportsGuestRuntimeErrors() {
    return Stream.of(
            failure("printLine([1][-1])", RuntimeErrorCode.INDEX_OUT_OF_BOUNDS),
            failure("printLine([1][1])", RuntimeErrorCode.INDEX_OUT_OF_BOUNDS),
            failure(
                "List<Integer> values = List<Integer>() printLine(values[-1])",
                RuntimeErrorCode.INDEX_OUT_OF_BOUNDS),
            failure(
                "List<Integer> values = List<Integer>() printLine(values[0])",
                RuntimeErrorCode.INDEX_OUT_OF_BOUNDS),
            failure(
                "Map<String, Integer> values = Map<String, Integer>() printLine(values[\"missing\"])",
                RuntimeErrorCode.MISSING_MAP_KEY),
            failure(
                "Stack<Integer> values = Stack<Integer>() printLine(values.pop())",
                RuntimeErrorCode.EMPTY_COLLECTION),
            failure(
                "Stack<Integer> values = Stack<Integer>() printLine(values.peek())",
                RuntimeErrorCode.EMPTY_COLLECTION),
            failure(
                "Queue<Integer> values = Queue<Integer>() printLine(values.remove())",
                RuntimeErrorCode.EMPTY_COLLECTION),
            failure(
                "Queue<Integer> values = Queue<Integer>() printLine(values.peek())",
                RuntimeErrorCode.EMPTY_COLLECTION),
            failure(
                "Deque<Integer> values = Deque<Integer>() printLine(values.removeFirst())",
                RuntimeErrorCode.EMPTY_COLLECTION),
            failure(
                "Deque<Integer> values = Deque<Integer>() printLine(values.removeLast())",
                RuntimeErrorCode.EMPTY_COLLECTION),
            failure(
                "Deque<Integer> values = Deque<Integer>() printLine(values.peekFirst())",
                RuntimeErrorCode.EMPTY_COLLECTION),
            failure(
                "Deque<Integer> values = Deque<Integer>() printLine(values.peekLast())",
                RuntimeErrorCode.EMPTY_COLLECTION),
            failure("printLine(1 / 0)", RuntimeErrorCode.DIVISION_BY_ZERO),
            failure("printLine(1 % 0)", RuntimeErrorCode.DIVISION_BY_ZERO))
        .map(
            testCase ->
                DynamicTest.dynamicTest(
                    testCase.code() + ": " + testCase.body(),
                    () -> assertFailure(testCase.body(), testCase.code())));
  }

  @Test
  void executionContextOwnsProcessInputs() {
    StringWriter output = new StringWriter();
    ExecutionContext context =
        new ExecutionContext(
            java.io.Reader.nullReader(),
            new PrintWriter(output),
            java.util.List.of("first", "second"),
            () -> false);

    assertEquals(java.util.List.of("first", "second"), context.arguments());
    assertFalse(context.cancellation().getAsBoolean());
  }

  @Test
  void observesCancellationAtLoopBoundaries() {
    Path path = Path.of("cancelled.norm").toAbsolutePath();
    var compilation =
        new Compiler()
            .compile(
                SourceFile.of(
                    path,
                    "Void main() { for value : range(start: 0, end: 10) { printLine(value) } }"));
    ExecutionContext context =
        new ExecutionContext(
            java.io.Reader.nullReader(),
            new PrintWriter(new StringWriter()),
            java.util.List.of(),
            () -> true);

    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () -> new ProgramRunner().run(compilation.program().orElseThrow(), context));

    assertEquals(RuntimeErrorCode.CANCELLED, exception.code());
    assertEquals(path.toUri(), exception.uri());
  }

  private static Failure failure(String body, RuntimeErrorCode code) {
    return new Failure(body, code);
  }

  private static void assertFailure(String body, RuntimeErrorCode code) {
    Path path = Path.of("runtime-error.norm").toAbsolutePath();
    String source = "Void main() {\n  " + body + "\n}";
    var compilation = new Compiler().compile(SourceFile.of(path, source));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());

    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                new ProgramRunner()
                    .run(compilation.program().orElseThrow(), new PrintWriter(new StringWriter())));

    assertEquals(code, exception.code());
    assertEquals(path.toUri(), exception.uri());
    assertEquals(2, exception.line());
    assertTrue(exception.column() >= 3);
    assertTrue(exception.isGuestException());
    assertFalse(exception.guestStack().isEmpty());
  }

  private record Failure(String body, RuntimeErrorCode code) {}
}
