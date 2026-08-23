package dev.w0fv1.norm.truffle;

import static dev.w0fv1.norm.testing.NormTestKit.projectSuite;
import static dev.w0fv1.norm.testing.NormTestKit.suite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class ProgramExecutionTest {
  @Test
  void preservesSourceOrderWhenBindingNamedArguments() throws Exception {
    assertOutput(
        "class Counter { Integer value Integer next() { value = value + 1 return value } } "
            + "Integer combine(Integer left, Integer right) { return left * 10 + right } "
            + "Void main() { Counter counter = Counter(value: 0) "
            + "printLine(combine(right: counter.next(), left: counter.next())) }",
        "21" + System.lineSeparator());
  }

  @Test
  void givesClassesIdentityAndCopyCreatesANewIdentity() throws Exception {
    assertOutput(
        "class Box { Integer value Void set(Integer next) { value = next } } "
            + "class Holder { Box box } "
            + "Void main() { "
            + "Box first = Box(value: 4) Box shared = first shared.set(9) "
            + "printLine(first.value) printLine(first == shared) "
            + "Box copied = first.copy() copied.set(12) "
            + "printLine(first.value) printLine(copied.value) printLine(first == copied) "
            + "Holder holder = Holder(box: first) Holder holderCopy = holder.copy() "
            + "holderCopy.box.set(15) printLine(holder.box.value) }",
        String.join(System.lineSeparator(), "9", "true", "9", "12", "false", "15", ""));
  }

  @Test
  void copiesValueContainersButSharesTheirClassElements() throws Exception {
    assertOutput(
        "class Box { Integer value Void set(Integer next) { value = next } } "
            + "Void main() { List<Box> first = List<Box>() Box box = Box(value: 1) first.add(box) "
            + "List<Box> second = first second.add(Box(value: 2)) Box secondBox = second[0] secondBox.set(7) "
            + "Box firstBox = first[0] printLine(first.size()) printLine(second.size()) printLine(firstBox.value) "
            + "List<Box> same = List<Box>() same.add(box) printLine(first == same) }",
        String.join(System.lineSeparator(), "1", "2", "7", "true", ""));
  }

  @Test
  void comparesValueContainersStructurally() throws Exception {
    assertOutput(
        "Void main() { "
            + "List<Integer> left = List<Integer>() left.add(1) List<Integer> right = List<Integer>() right.add(1) printLine(left == right) "
            + "Pair<List<Integer>, Integer> first = Pair<List<Integer>, Integer>(first: left, second: 2) Pair<List<Integer>, Integer> second = Pair<List<Integer>, Integer>(first: right, second: 2) printLine(first == second) "
            + "Map<Array<Integer>, Integer> values = Map<Array<Integer>, Integer>() values.put(key: [1, 2], value: 7) printLine(values[[1, 2]]) "
            + "Set<Array<Integer>> unique = Set<Array<Integer>>() unique.add([3, 4]) printLine(unique.contains([3, 4])) }",
        String.join(System.lineSeparator(), "true", "true", "7", "true", ""));
  }

  @Test
  void bindsNamedArgumentsForBuiltins() throws Exception {
    assertOutput(
        "import std.math.min import std.math.max "
            + "Void main() { printLine(min(right: 8, left: 3)) printLine(max(right: 8, left: 3)) }",
        String.join(System.lineSeparator(), "3", "8", ""));
  }

  @Test
  void executesGenericCollectionsWithSize() throws Exception {
    assertOutput(
        "Void main() { List<Integer> values = List<Integer>() values.add(3) values.add(8) "
            + "printLine(values.size()) printLine(values[1]) }",
        String.join(System.lineSeparator(), "2", "8", ""));
  }

  @Test
  void executesGenericFunctions() throws Exception {
    assertOutput(
        "T identity<T>(T value) { return value } "
            + "Void main() { printLine(identity(value: 9)) printLine(identity(value: \"Norm\")) }",
        String.join(System.lineSeparator(), "9", "Norm", ""));
  }

  @Test
  void mutatesValueFieldsThroughAClassMemberPath() throws Exception {
    assertOutput(
        "class Box<T> { T value } "
            + "Void main() { Box<List<Integer>> box = Box<List<Integer>>(value: List<Integer>()) "
            + "box.value.add(9) printLine(box.value[0]) }",
        "9" + System.lineSeparator());
  }

  @Test
  void executesCoreTextAndMathOperations() throws Exception {
    assertOutput(
        "import std.math.clamp import std.math.sign "
            + "Void main() { printLine(\"A😀\".byteSize()) printLine(\"A😀\".codePointSize()) "
            + "printLine(\"👨‍👩‍👧‍👦\".graphemeSize()) printLine(clamp(value: 12, minimum: 0, maximum: 9)) "
            + "printLine(sign(-4)) }",
        String.join(System.lineSeparator(), "5", "2", "1", "9", "-1", ""));
  }

  @Test
  void exposesExplicitUnicodeStringViews() throws Exception {
    assertOutput(
        "Void main() { "
            + "Array<CodePoint> points = \"A😀\".codePoints() "
            + "Array<String> graphemes = \"é😀\".graphemes() "
            + "printLine(points.size()) printLine(points[0]) printLine(points[1]) "
            + "printLine(graphemes.size()) printLine(graphemes[0]) "
            + "printLine(\"A😀B\".sliceCodePoints(start: 1, end: 2)) "
            + "printLine('9'.scalarValue()) printLine(\"/a/b/\".split(separator: \"/\").size()) }",
        String.join(System.lineSeparator(), "2", "A", "😀", "2", "é", "😀", "57", "4", ""));
  }

  @Test
  void executesUnicodeStringOperations() throws Exception {
    assertOutput(
        "Void main() { "
            + "printLine(\"\".isEmpty()) printLine(\"Norm😀\".contains(value: \"rm😀\")) "
            + "printLine(\"Norm\".startsWith(prefix: \"No\")) printLine(\"Norm\".endsWith(suffix: \"rm\")) "
            + "printLine(\"a😀b\".sliceGraphemes(start: 1, end: 2)) "
            + "printLine(\"one two one\".replace(target: \"one\", replacement: \"1\")) "
            + "printLine(\"one two one\".replaceFirst(target: \"one\", replacement: \"1\")) "
            + "printLine(\"  Norm  \".trim()) printLine(\"  Norm  \".trimStart()) "
            + "printLine(\"  Norm  \".trimEnd()) printLine(\"NORM\".toLowercase()) "
            + "printLine(\"norm\".toUppercase()) printLine(\"straße\".toUppercase()) "
            + "printLine(\"Content-Type\".equalsIgnoreCaseAscii(other: \"content-type\")) "
            + "printLine(\"a😀\".compareCodePoints(right: \"a😁\")) "
            + "printLine('9'.isDecimalDigit()) printLine('字'.isLetter()) "
            + "printLine(' '.isWhitespace()) printLine('A'.isUppercase()) printLine('a'.isLowercase()) "
            + "printLine(\" Norm \".trim()) }",
        String.join(
            System.lineSeparator(),
            "true",
            "true",
            "true",
            "true",
            "😀",
            "1 two 1",
            "1 two one",
            "Norm",
            "Norm  ",
            "  Norm",
            "norm",
            "NORM",
            "STRASSE",
            "true",
            "-1",
            "true",
            "true",
            "true",
            "true",
            "true",
            "Norm",
            ""));
  }

  @Test
  void iteratesMapsAndStacksWithTheirDeclaredElementTypes() throws Exception {
    assertOutput(
        "Void main() { Map<String, Integer> values = Map<String, Integer>() "
            + "values.put(key: \"first\", value: 1) values.put(key: \"second\", value: 2) "
            + "for Pair<String, Integer> entry : values { printLine(entry.first) printLine(entry.second) } "
            + "Stack<Integer> stack = Stack<Integer>() stack.push(3) stack.push(7) "
            + "for Integer value : stack { printLine(value) } }",
        String.join(System.lineSeparator(), "first", "1", "second", "2", "7", "3", ""));
  }

  @Test
  void rejectsMissingMapKeysAtRuntime() {
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput(
                    "Void main() { Map<String, Integer> values = Map<String, Integer>() printLine(values[\"missing\"]) }",
                    ""));
    assertTrue(exception.getMessage().contains("map key does not exist"));
    assertEquals(RuntimeErrorCode.MISSING_MAP_KEY, exception.code());
  }

  @Test
  void returnsNullableValuesFromMapGet() throws Exception {
    assertOutput(
        "Void main() { Map<String, Integer> values = Map<String, Integer>() "
            + "values.put(key: \"answer\", value: 42) "
            + "printLine(values.get(key: \"answer\") ?? -1) "
            + "printLine(values.get(key: \"missing\") ?? -1) "
            + "Map<String, String?> nullable = Map<String, String?>() "
            + "nullable.put(key: \"saved\", value: null) "
            + "printLine(nullable.containsKey(key: \"saved\")) "
            + "printLine(nullable.get(key: \"saved\") == null) }",
        String.join(System.lineSeparator(), "42", "-1", "true", "true", ""));
  }

  @Test
  void executesNullFlowSafeAccessAndCoalescingWithShortCircuiting() throws Exception {
    assertOutput(
        "class User { String name } "
            + "String fallback() { printLine(\"fallback\") return \"guest\" } "
            + "Void main() { User? missing = null User? present = User(name: \"Norm\") "
            + "printLine(missing?.name ?? fallback()) "
            + "printLine(present?.name ?? fallback()) "
            + "if present != null { printLine(present.name) } }",
        String.join(System.lineSeparator(), "fallback", "guest", "Norm", "Norm", ""));
  }

  @Test
  void evaluatesSafeReceiversOnceAndSkipsArgumentsWhenNull() throws Exception {
    assertOutput(
        "class Receiver { String use(String value) { return value } } "
            + "Receiver? receiver(Integer call) { printLine(call) return null } "
            + "String argument() { printLine(\"argument\") return \"value\" } "
            + "Void main() { printLine(receiver(call: 1)?.use(value: argument()) ?? \"missing\") }",
        String.join(System.lineSeparator(), "1", "missing", ""));
  }

  @Test
  void executesConditionalForWithBreakAndContinue() throws Exception {
    assertOutput(
        "Void main() { Integer value = 0 for value < 6 { value = value + 1 "
            + "if value == 2 { continue } if value == 5 { break } printLine(value) } }",
        String.join(System.lineSeparator(), "1", "3", "4", ""));
  }

  @Test
  void executesResolvedFunctionAndMethodOverloads() throws Exception {
    assertOutput(
        "Integer choose(Integer value) { return value + 1 } "
            + "String choose(String value) { return value + \"!\" } "
            + "class Picker { Integer choose(Integer value) { return value * 2 } "
            + "String choose(String value) { return value + value } } "
            + "Void main() { Picker picker = Picker() printLine(choose(value: 3)) "
            + "printLine(choose(value: \"N\")) printLine(picker.choose(value: 4)) "
            + "printLine(picker.choose(value: \"A\")) }",
        String.join(System.lineSeparator(), "4", "N!", "8", "AA", ""));
  }

  @Test
  void iteratesSteppedRangesInBothDirectionsWithoutOverflow() throws Exception {
    assertOutput(
        "Void main() { Range ascending = range(start: 0, end: 7, step: 2) "
            + "printLine(ascending.size()) for value : ascending { printLine(value) } "
            + "Range descending = range(start: 5, end: -2, step: -3) "
            + "printLine(descending.size()) for value : descending { printLine(value) } "
            + "for value : range(start: 9223372036854775806, end: 9223372036854775807, step: 2) "
            + "{ printLine(value) } }",
        String.join(
            System.lineSeparator(),
            "4",
            "0",
            "2",
            "4",
            "6",
            "3",
            "5",
            "2",
            "-1",
            "9223372036854775806",
            ""));
  }

  @Test
  void rejectsZeroRangeStepAtRuntime() {
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () -> assertOutput("Void main() { range(start: 0, end: 4, step: 0) }", ""));

    assertEquals(RuntimeErrorCode.INVALID_ARGUMENT, exception.code());
  }

  @Test
  void executesFilledLastRemoveLastAndReversedSequenceOperations() throws Exception {
    assertOutput(
        "Void main() { List<Integer> values = List.filled(size: 3, value: 7) values[0] = 1 "
            + "printLine(values.last()) printLine(values.removeLast()) printLine(values.size()) "
            + "List<Integer> reversed = values.reversed() for value : reversed { printLine(value) } "
            + "Array<Boolean> flags = Array.filled(size: 2, value: false) flags[1] = true "
            + "for flag : flags.reversed() { printLine(flag) } }",
        String.join(System.lineSeparator(), "7", "7", "2", "7", "1", "true", "false", ""));
  }

  @Test
  void executesAsciiDigitOperationsAndRejectsNonDigits() throws Exception {
    assertOutput(
        "Void main() { printLine('7'.isAsciiDigit()) printLine('７'.isAsciiDigit()) "
            + "printLine('7'.asciiDigitValue()) }",
        String.join(System.lineSeparator(), "true", "false", "7", ""));
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () -> assertOutput("Void main() { 'x'.asciiDigitValue() }", ""));
    assertEquals(RuntimeErrorCode.INVALID_ARGUMENT, exception.code());
  }

  @Test
  void rejectsInvalidFilledSizesAndEmptySequenceTailOperations() {
    NormExecutionException negative =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput(
                    "Void main() { List<Integer> values = List.filled(size: -1, value: 0) }", ""));
    NormExecutionException empty =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput(
                    "Void main() { List<Integer> values = List<Integer>() values.last() }", ""));
    assertEquals(RuntimeErrorCode.INVALID_ARGUMENT, negative.code());
    assertEquals(RuntimeErrorCode.EMPTY_COLLECTION, empty.code());
  }

  @TestFactory
  Stream<DynamicTest> runsBasicLanguagePrograms() throws Exception {
    return suite("base");
  }

  @TestFactory
  Stream<DynamicTest> runsSingleFileAlgorithms() throws Exception {
    return suite("algorithms");
  }

  @TestFactory
  Stream<DynamicTest> runsLeetCodeAlgorithms() throws Exception {
    return suite("algorithms/leetcode");
  }

  @TestFactory
  Stream<DynamicTest> runsClassPrograms() throws Exception {
    return suite("class");
  }

  @TestFactory
  Stream<DynamicTest> runsGenericPrograms() throws Exception {
    return suite("generics");
  }

  @TestFactory
  Stream<DynamicTest> runsNullablePrograms() throws Exception {
    return suite("nullable");
  }

  @TestFactory
  Stream<DynamicTest> runsOverloadPrograms() throws Exception {
    return suite("overloads");
  }

  @TestFactory
  Stream<DynamicTest> runsRangePrograms() throws Exception {
    return suite("range");
  }

  @TestFactory
  Stream<DynamicTest> runsMultiFilePrograms() throws Exception {
    return projectSuite("projects");
  }

  private static void assertOutput(String text, String expected) throws Exception {
    var compilation = new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));
    assertEquals(expected, output.toString());
  }
}
