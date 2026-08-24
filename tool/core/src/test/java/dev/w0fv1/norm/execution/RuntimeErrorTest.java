package dev.w0fv1.norm.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
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
            failure(
                "printLine(\"Norm\".sliceCodePoints(start: 0, end: 5))",
                RuntimeErrorCode.INDEX_OUT_OF_BOUNDS),
            failure(
                "printLine(\"😀\".sliceGraphemes(start: 1, end: 0))",
                RuntimeErrorCode.INDEX_OUT_OF_BOUNDS),
            failure(
                "printLine(\"Norm\".split(separator: \"\"))", RuntimeErrorCode.INVALID_ARGUMENT),
            failure(
                "printLine(\"Norm\".replace(target: \"\", replacement: \"x\"))",
                RuntimeErrorCode.INVALID_ARGUMENT),
            failure(
                "printLine(\"Norm\".replaceFirst(target: \"\", replacement: \"x\"))",
                RuntimeErrorCode.INVALID_ARGUMENT),
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

  @Test
  void preservesTheCalledAuthoringOccurrenceForSharedDefinitions() {
    Path firstPath = Path.of("occurrences/a.norm").toAbsolutePath();
    Path secondPath = Path.of("occurrences/z.norm").toAbsolutePath();
    Path entryPath = Path.of("occurrences/main.norm").toAbsolutePath();
    SourceFile first = SourceFile.of(firstPath, "Integer alpha() { return 1 / 0 }");
    SourceFile second = SourceFile.of(secondPath, "Integer beta() { return 1 / 0 }");
    SourceFile entry = SourceFile.of(entryPath, "Void main() { printLine(beta()) }");
    var compilation =
        new Compiler().compile(new CompilationRequest(entry.id(), List.of(first, second, entry)));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());

    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                new ProgramRunner()
                    .run(compilation.program().orElseThrow(), new PrintWriter(new StringWriter())));

    assertEquals(secondPath.toUri(), exception.uri());
    assertEquals("beta", exception.guestStack().getFirst().name());
    assertEquals(secondPath.toUri(), exception.guestStack().getFirst().uri());
  }

  @Test
  void preservesEveryOccurrenceAcrossSharedCallerAndLeafDefinitions() {
    Path firstLeafPath = Path.of("occurrence-chain/a-leaf.norm").toAbsolutePath();
    Path secondLeafPath = Path.of("occurrence-chain/z-leaf.norm").toAbsolutePath();
    Path firstCallerPath = Path.of("occurrence-chain/a-caller.norm").toAbsolutePath();
    Path secondCallerPath = Path.of("occurrence-chain/z-caller.norm").toAbsolutePath();
    SourceFile firstLeaf = SourceFile.of(firstLeafPath, "Integer leafA() { return 1 / 0 }");
    SourceFile secondLeaf = SourceFile.of(secondLeafPath, "Integer leafB() { return 1 / 0 }");
    SourceFile firstCaller = SourceFile.of(firstCallerPath, "Integer callerA() { return leafA() }");
    SourceFile secondCaller =
        SourceFile.of(secondCallerPath, "Integer callerB() { return leafB() }");
    SourceFile entry =
        SourceFile.of(
            Path.of("occurrence-chain/main.norm").toAbsolutePath(),
            "Void main() { printLine(callerB()) }");
    var compilation =
        new Compiler()
            .compile(
                new CompilationRequest(
                    entry.id(), List.of(firstLeaf, secondLeaf, firstCaller, secondCaller, entry)));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());

    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                new ProgramRunner()
                    .run(compilation.program().orElseThrow(), new PrintWriter(new StringWriter())));

    assertEquals(
        List.of("leafB", "callerB"),
        exception.guestStack().stream().limit(2).map(GuestStackFrame::name).toList());
    assertEquals(
        List.of(secondLeafPath.toUri(), secondCallerPath.toUri()),
        exception.guestStack().stream().limit(2).map(GuestStackFrame::uri).toList());
  }

  @Test
  void preservesOccurrenceRoutesInsideSymmetricRecursiveGroups() {
    Path alphaPath = Path.of("occurrence-cycle/a.norm").toAbsolutePath();
    Path betaPath = Path.of("occurrence-cycle/b.norm").toAbsolutePath();
    Path gammaPath = Path.of("occurrence-cycle/c.norm").toAbsolutePath();
    SourceFile alpha =
        SourceFile.of(
            alphaPath,
            "Integer alpha(Integer value) { if value == 0 { return 1 / 0 } "
                + "return beta(value - 1) }");
    SourceFile beta =
        SourceFile.of(
            betaPath,
            "Integer beta(Integer value) { if value == 0 { return 1 / 0 } "
                + "return gamma(value - 1) }");
    SourceFile gamma =
        SourceFile.of(
            gammaPath,
            "Integer gamma(Integer value) { if value == 0 { return 1 / 0 } "
                + "return alpha(value - 1) }");
    SourceFile entry =
        SourceFile.of(
            Path.of("occurrence-cycle/main.norm").toAbsolutePath(),
            "Void main() { printLine(beta(2)) }");
    var compilation =
        new Compiler()
            .compile(new CompilationRequest(entry.id(), List.of(alpha, beta, gamma, entry)));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());

    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                new ProgramRunner()
                    .run(compilation.program().orElseThrow(), new PrintWriter(new StringWriter())));

    assertEquals(
        List.of("alpha", "gamma", "beta"),
        exception.guestStack().stream().limit(3).map(GuestStackFrame::name).toList());
    assertEquals(
        List.of(alphaPath.toUri(), gammaPath.toUri(), betaPath.toUri()),
        exception.guestStack().stream().limit(3).map(GuestStackFrame::uri).toList());
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
