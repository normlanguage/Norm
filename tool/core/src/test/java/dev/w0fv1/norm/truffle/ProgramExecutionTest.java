package dev.w0fv1.norm.truffle;

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
        "class Counter { int value int next() { value = value + 1 return value } } "
            + "int combine(int left, int right) { return left * 10 + right } "
            + "void main() { Counter counter = Counter(value: 0) "
            + "print(combine(right: counter.next(), left: counter.next())) }",
        "21" + System.lineSeparator());
  }

  @Test
  void givesClassesIdentityAndCopyCreatesANewIdentity() throws Exception {
    assertOutput(
        "class Box { int value void set(int next) { value = next } } "
            + "class Holder { Box box } "
            + "void main() { "
            + "Box first = Box(value: 4) Box shared = first shared.set(9) "
            + "print(first.value) print(first == shared) "
            + "Box copied = first.copy() copied.set(12) "
            + "print(first.value) print(copied.value) print(first == copied) "
            + "Holder holder = Holder(box: first) Holder holderCopy = holder.copy() "
            + "holderCopy.box.set(15) print(holder.box.value) }",
        String.join(System.lineSeparator(), "9", "true", "9", "12", "false", "15", ""));
  }

  @Test
  void copiesValueContainersButSharesTheirClassElements() throws Exception {
    assertOutput(
        "class Box { int value void set(int next) { value = next } } "
            + "void main() { List<Box> first = List<Box>() Box box = Box(value: 1) first.add(box) "
            + "List<Box> second = first second.add(Box(value: 2)) Box secondBox = second[0] secondBox.set(7) "
            + "Box firstBox = first[0] print(first.size()) print(second.size()) print(firstBox.value) "
            + "List<Box> same = List<Box>() same.add(box) print(first == same) }",
        String.join(System.lineSeparator(), "1", "2", "7", "true", ""));
  }

  @Test
  void comparesValueContainersStructurally() throws Exception {
    assertOutput(
        "void main() { "
            + "List<int> left = List<int>() left.add(1) List<int> right = List<int>() right.add(1) print(left == right) "
            + "Pair<List<int>, int> first = Pair<List<int>, int>(first: left, second: 2) Pair<List<int>, int> second = Pair<List<int>, int>(first: right, second: 2) print(first == second) "
            + "Map<Array<int>, int> values = Map<Array<int>, int>() values.put(key: [1, 2], value: 7) print(values[[1, 2]]) "
            + "Set<Array<int>> unique = Set<Array<int>>() unique.add([3, 4]) print(unique.contains([3, 4])) }",
        String.join(System.lineSeparator(), "true", "true", "7", "true", ""));
  }

  @Test
  void bindsNamedArgumentsForBuiltins() throws Exception {
    assertOutput(
        "import std.math.min import std.math.max "
            + "void main() { print(min(right: 8, left: 3)) print(max(right: 8, left: 3)) }",
        String.join(System.lineSeparator(), "3", "8", ""));
  }

  @Test
  void executesGenericCollectionsWithSize() throws Exception {
    assertOutput(
        "void main() { List<int> values = List<int>() values.add(3) values.add(8) "
            + "print(values.size()) print(values[1]) }",
        String.join(System.lineSeparator(), "2", "8", ""));
  }

  @Test
  void executesGenericFunctions() throws Exception {
    assertOutput(
        "T identity<T>(T value) { return value } "
            + "void main() { print(identity(value: 9)) print(identity(value: \"Norm\")) }",
        String.join(System.lineSeparator(), "9", "Norm", ""));
  }

  @Test
  void mutatesValueFieldsThroughAClassMemberPath() throws Exception {
    assertOutput(
        "class Box<T> { T value } "
            + "void main() { Box<List<int>> box = Box<List<int>>(value: List<int>()) "
            + "box.value.add(9) print(box.value[0]) }",
        "9" + System.lineSeparator());
  }

  @Test
  void executesCoreTextAndMathOperations() throws Exception {
    assertOutput(
        "import std.math.clamp import std.math.sign "
            + "void main() { print(\"A😀\".byteSize()) print(\"A😀\".codePointSize()) "
            + "print(\"👨‍👩‍👧‍👦\".graphemeSize()) print(clamp(value: 12, minimum: 0, maximum: 9)) "
            + "print(sign(-4)) }",
        String.join(System.lineSeparator(), "5", "2", "1", "9", "-1", ""));
  }

  @Test
  void iteratesMapsAndStacksWithTheirDeclaredElementTypes() throws Exception {
    assertOutput(
        "void main() { Map<String, int> values = Map<String, int>() "
            + "values.put(key: \"first\", value: 1) values.put(key: \"second\", value: 2) "
            + "for Pair<String, int> entry : values { print(entry.first) print(entry.second) } "
            + "Stack<int> stack = Stack<int>() stack.push(3) stack.push(7) "
            + "for int value : stack { print(value) } }",
        String.join(System.lineSeparator(), "first", "1", "second", "2", "7", "3", ""));
  }

  @Test
  void rejectsMissingMapKeysAtRuntime() {
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput(
                    "void main() { Map<String, int> values = Map<String, int>() print(values[\"missing\"]) }",
                    ""));
    assertTrue(exception.getMessage().contains("map key does not exist"));
    assertEquals(RuntimeErrorCode.MISSING_MAP_KEY, exception.code());
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
  Stream<DynamicTest> runsClassPrograms() throws Exception {
    return suite("class");
  }

  @TestFactory
  Stream<DynamicTest> runsGenericPrograms() throws Exception {
    return suite("generics");
  }

  private static void assertOutput(String text, String expected) throws Exception {
    var compilation = new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));
    assertEquals(expected, output.toString());
  }
}
