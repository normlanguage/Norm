package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
            + "void main() { List first = List() Box box = Box(value: 1) first.add(box) "
            + "List second = first second.add(Box(value: 2)) Box secondBox = second[0] secondBox.set(7) "
            + "Box firstBox = first[0] print(first.length) print(second.length) print(firstBox.value) "
            + "List same = List() same.add(box) print(first == same) }",
        String.join(System.lineSeparator(), "1", "2", "7", "true", ""));
  }

  @Test
  void comparesValueContainersStructurally() throws Exception {
    assertOutput(
        "void main() { "
            + "List left = List() left.add(1) List right = List() right.add(1) print(left == right) "
            + "Pair first = Pair(first: left, second: 2) Pair second = Pair(first: right, second: 2) print(first == second) "
            + "Map values = Map() values.put(key: [1, 2], value: 7) print(values.get([1, 2])) "
            + "Set unique = Set() unique.add([3, 4]) print(unique.contains([3, 4])) }",
        String.join(System.lineSeparator(), "true", "true", "7", "true", ""));
  }

  @Test
  void bindsNamedArgumentsForBuiltins() throws Exception {
    assertOutput(
        "void main() { print(min(right: 8, left: 3)) print(max(right: 8, left: 3)) }",
        String.join(System.lineSeparator(), "3", "8", ""));
  }

  @TestFactory
  Stream<DynamicTest> runsThirtyBasicLanguagePrograms() throws Exception {
    return suite("base", "basic language suite", 30);
  }

  @TestFactory
  Stream<DynamicTest> runsThirtySingleFileAlgorithms() throws Exception {
    return suite("algorithms", "algorithm suite", 30);
  }

  @TestFactory
  Stream<DynamicTest> runsFiveClassPrograms() throws Exception {
    return suite("class", "class suite", 5);
  }

  private static Stream<DynamicTest> suite(String resource, String name, int expectedSize)
      throws Exception {
    Path directory = resourceDirectory(resource);
    List<String> cases = Files.readAllLines(directory.resolve("cases.tsv"), StandardCharsets.UTF_8);
    assertEquals(expectedSize, cases.size(), name + " has an unexpected number of programs");
    return cases.stream()
        .map(
            line -> {
              String[] fields = line.split("\\t", 2);
              return DynamicTest.dynamicTest(
                  fields[0],
                  () ->
                      run(
                          directory.resolve(fields[0]),
                          fields[1].replace("\\n", System.lineSeparator())));
            });
  }

  private static void run(Path path, String expected) throws Exception {
    String text = Files.readString(path, StandardCharsets.UTF_8);
    var compilation = new Compiler().compile(SourceFile.of(path, text));
    assertTrue(
        compilation.isSuccess(),
        () -> "diagnostics for " + path + ": " + compilation.diagnostics());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));
    assertEquals(expected, output.toString());
  }

  private static void assertOutput(String text, String expected) throws Exception {
    var compilation = new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));
    assertEquals(expected, output.toString());
  }

  private static Path resourceDirectory(String resource) throws URISyntaxException {
    var url =
        Objects.requireNonNull(
            ProgramExecutionTest.class.getResource("/" + resource + "/cases.tsv"), resource);
    return Path.of(url.toURI()).getParent();
  }
}
