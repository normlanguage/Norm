package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FunctionExecutionTest {
  @Test
  void defaultsTopLevelFunctionsToVoidAndClassMethodsToTheirReceiver() throws Exception {
    assertOutput(
        "class Box<T> { T value set(T next) { value = next } "
            + "keep(Boolean unchanged, T next) { if unchanged { return } value = next } "
            + "invoke() { Function<Void()> action = () { return } action() } } "
            + "announce() { printLine(\"ready\") } "
            + "main() { Box<Integer> box = Box<Integer>(value: 1) "
            + "Box<Integer> result = box.set(2).keep(unchanged: true, next: 3).invoke() "
            + "announce() printLine(result.value) printLine(result == box) }",
        lines("ready", "2", "true"));
  }

  @Test
  void referencesFluentMethodsWithTheirReceiverResultType() throws Exception {
    assertOutput(
        "class Counter { Integer value add(Integer amount) { value = value + amount } } "
            + "main() { Counter counter = Counter(value: 4) "
            + "Function<Counter(Integer)> add = counter::add "
            + "Counter result = add(5) printLine(result.value) printLine(result == counter) }",
        lines("9", "true"));
  }

  @Test
  void invokesContextTypedAndInferredLambdas() throws Exception {
    assertOutput(
        "Void main() { Function<Integer(Integer)> first = (value) { value * 2 } "
            + "var second = (Integer value) { value + 3 } "
            + "printLine(first(4)) printLine(second(4)) }",
        lines("8", "7"));
  }

  @Test
  void capturesEffectivelyFinalValues() throws Exception {
    assertOutput(
        "Void main() { Integer factor = 3 var multiply = (Integer value) { value * factor } "
            + "printLine(multiply(5)) }",
        lines("15"));
  }

  @Test
  void createsTopLevelAndBoundMethodReferences() throws Exception {
    assertOutput(
        "Integer doubled(Integer value) { return value * 2 } "
            + "class Counter { Integer value Integer add(Integer amount) { value = value + amount return value } } "
            + "Void main() { Function<Integer(Integer)> first = doubled "
            + "Counter counter = Counter(value: 10) Function<Integer(Integer)> second = counter::add "
            + "printLine(first(6)) printLine(second(5)) printLine(counter.value) }",
        lines("12", "15", "15"));
  }

  @Test
  void passesFunctionObjectsAndSupportsDeclarationSugar() throws Exception {
    assertOutput(
        "Integer apply(Integer transform(Integer value), Integer input) { return transform(input) } "
            + "class Counter { Integer value Integer add(Integer amount) { return value + amount } } "
            + "Void main() { var doubled = Integer(Integer value) { value * 2 } "
            + "Counter counter = Counter(value: 10) Integer add(Integer amount) = counter::add "
            + "printLine(apply(transform: doubled, input: 6)) printLine(add(5)) }",
        lines("12", "15"));
  }

  @Test
  void dispatchesInterfaceDefaultMethodsThroughTheReceiver() throws Exception {
    assertOutput(
        "interface Incrementable { Integer base() Integer plus(Integer amount) { return this.base() + amount } } "
            + "class Counter implements Incrementable { Integer value public Integer base() { return value } } "
            + "Void main() { Counter counter = Counter(value: 7) printLine(counter.plus(5)) }",
        lines("12"));
  }

  @Test
  void mapsIterableValuesWithBidirectionalLambdaInference() throws Exception {
    assertOutput(
        "Void main() { List<Integer> values = [1, 2, 3] "
            + "List<Integer> doubled = values.map((value) { value * 2 }) "
            + "for value : doubled { printLine(value) } }",
        lines("2", "4", "6"));
  }

  @Test
  void returnsEscapingClosuresAndInvokesFunctionFields() throws Exception {
    assertOutput(
        "Function<Integer(Integer)> multiplier(Integer factor) { "
            + "return (Integer value) { value * factor } } "
            + "class Transformer { Function<Integer(Integer)> operation } "
            + "Void main() { Transformer transformer = Transformer(operation: multiplier(4)) "
            + "printLine(transformer.operation(6)) }",
        lines("24"));
  }

  @Test
  void invokesFunctionFieldsOnArbitraryReceiverExpressions() throws Exception {
    assertOutput(
        "class Transformer { Function<Integer(Integer)> operation } "
            + "Transformer transformer() { "
            + "return Transformer(operation: (Integer value) { value * 5 }) } "
            + "Void main() { printLine(transformer().operation(6)) }",
        lines("30"));
  }

  @Test
  void specializesGenericFunctionAndMethodReferences() throws Exception {
    assertOutput(
        "T identity<T>(T value) { return value } "
            + "class Identity { T apply<T>(T value) { return value } } "
            + "Void main() { Function<Integer(Integer)> first = identity "
            + "Identity identityObject = Identity() "
            + "Function<String(String)> second = identityObject::apply "
            + "printLine(first(9)) printLine(second(\"nine\")) }",
        lines("9", "nine"));
  }

  @Test
  void carriesGenericRuntimeTypesIntoNestedLambdas() throws Exception {
    assertOutput(
        "Function<List<T>(T)> singleton<T>() { "
            + "return (T value) { List<T> result = List<>() result.add(value) return result } } "
            + "Void main() { Function<List<Integer>(Integer)> make = singleton<Integer>() "
            + "List<Integer> values = make(7) printLine(values[0]) }",
        lines("7"));
  }

  private static void assertOutput(String text, String expected) throws Exception {
    var compilation = new Compiler().compile(SourceFile.of(Path.of("functions.norm"), text));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));
    assertEquals(expected, output.toString());
  }

  private static String lines(String... values) {
    return String.join(System.lineSeparator(), values) + System.lineSeparator();
  }
}
