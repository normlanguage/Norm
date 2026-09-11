package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class FunctionExecutionTest {
  @Test
  void executesExplicitGenericTrailingLambdasAcrossControlFlowBoundaries() throws Exception {
    assertOutput(
        """
        T submit<T>(Function<T()> work) { work() }
        class Runner { T run<T>(Function<T()> work) { work() } }
        Void main() {
          printLine(submit<Integer> { 42 })
          var names = submit<List<String>> { ["Norm"] }
          printLine(names[0])
          printLine(Runner().run<String> { "Todo" })
          if (submit<Boolean> { true }) { printLine("entered") }
          if 1 < 2 { printLine("comparison") }
        }
        """,
        lines("42", "Norm", "Todo", "entered", "comparison"));
  }

  @Test
  void callsNonGenericParentMethodsFromGenericChildren() throws Exception {
    assertOutput(
        """
        class Base { Integer read() { 42 } }
        class Child<T> extends Base {
          Integer direct() { this.read() }
          Function<Integer()> bound() { this.read }
        }
        Void main() {
          var child = Child<String>()
          printLine(child.direct())
          var read = child.bound()
          printLine(read())
        }
        """,
        lines("42", "42"));
  }

  @Test
  void recognizesValueDeclarationsWithDefaultAndBoundedTypeParameters() throws Exception {
    assertOutput(
        """
        interface Config {}
        value DefaultConfig implements Config {}
        value NumberConfig implements Config { Integer number = 42 }
        value Choice<T extends Config = DefaultConfig> { T? config = null }
        Void main() {
          printLine(Choice().config == null)
          printLine(Choice(config: NumberConfig()).config!!.number)
        }
        """,
        lines("true", "42"));
  }

  @Test
  void evaluatesIfExpressionsWithNarrowingAndEarlyReturns() throws Exception {
    assertOutput(
        """
        class Counter { Integer calls = 0 Boolean check() { calls = calls + 1; true } }
        String describe(String? text) { if text != null { text } else { "empty" } }
        Integer selected(Boolean early) {
          Integer value = if early { return 7 } else { 9 }
          value + 1
        }
        Void main() {
          var counter = Counter()
          printLine(if counter.check() { 1 } else { 2 })
          printLine(counter.calls)
          String? optional = "present"
          var text = if optional != null { optional } else { "missing" }
          printLine(text)
          printLine(if false { "first" } else if true { "second" } else { "third" })
          printLine(selected(true))
          printLine(selected(false))
        }
        """,
        lines("1", "1", "present", "second", "7", "10"));
  }

  @Test
  void returnsFinalExpressionsAcrossCallableKinds() throws Exception {
    assertOutput(
        """
        interface Reading { Integer base() Integer doubled() { this.base() * 2 } }
        class Counter implements Reading {
          Integer number
          Integer base() { number }
          Integer next { get { number + 1 } }
          Integer add(Integer amount) { number = number + amount; number }
        }
        T identity<T>(T value) { value }
        Integer choose(Boolean first) {
          if first { 10 } else { 20 }
        }
        Integer early(Boolean stop) { if stop { return 3 } 4 }
        Void main() {
          Function<Integer(Boolean)> select = (flag) { if flag { 30 } else { 40 } }
          var counter = Counter(number: 5)
          printLine(counter.doubled())
          printLine(counter.next)
          printLine(counter.add(2))
          printLine(identity("value"))
          printLine(choose(true))
          printLine(choose(false))
          printLine(early(true))
          printLine(early(false))
          printLine(select(true))
          printLine(select(false))
        }
        """,
        lines("10", "6", "7", "value", "10", "20", "3", "4", "30", "40"));
  }

  @Test
  void executesDefaultLambdasFromDistinctCallers() throws Exception {
    assertOutput(
        """
        Void invoke(Function<Void()> action = () { printLine("default") }) { action() }
        Void first() { invoke() }
        Void second() { invoke() }
        Void main() { first(); second(); invoke() }
        """,
        lines("default", "default", "default"));
  }

  @Test
  void invokesTrailingLambdasWithInferenceDefaultsAndNestedCalls() throws Exception {
    assertOutput(
        """
        T build<T>(String label, Function<T(Integer)> body, Integer input = 4) {
          printLine(label)
          return body(input)
        }
        Integer run(Function<Integer()> body) { return body() }
        Void main() {
          var offset = 3
          printLine(build(label: "outer") { value in
            run() { value + offset }
          })
          printLine(run { 9 })
        }
        """,
        lines("outer", "7", "9"));
  }

  @Test
  void preservesControlFlowBracesAroundCalls() throws Exception {
    assertOutput(
        """
        Boolean ready() { return true }
        List<Integer> numbers() { return [1, 2] }
        Boolean test(Function<Boolean()> body) { return body() }
        Void main() {
          if ready() { printLine("ready") }
          for number : numbers() { printLine(number) }
          if (test() { true }) { printLine("nested") }
        }
        """,
        lines("ready", "1", "2", "nested"));
  }

  @Test
  void bindsTrailingLambdasForConstructorsMethodsAndMultipleParameters() throws Exception {
    assertOutput(
        """
        class Operation {
          Function<Integer(Integer, Integer)> body
          Operation(Function<Integer(Integer, Integer)> body) { this.body = body }
          Integer apply(Integer value, Function<Integer(Integer)> transform) {
            return transform(body(value, 2))
          }
        }
        Void main() {
          var operation = Operation() { left, right in left + right }
          printLine(operation.apply(value: 3) { result in result * 2 })
        }
        """,
        lines("10"));
  }

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
            + "Function<Counter(Integer)> add = counter.add "
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
  void createsTopLevelAndBoundFunctionValues() throws Exception {
    assertOutput(
        "Integer doubled(Integer value) { return value * 2 } class Counter { Integer value Integer"
            + " add(Integer amount) { value = value + amount return value } } Void main() {"
            + " Function<Integer(Integer)> first = doubled Counter counter = Counter(value: 10)"
            + " Function<Integer(Integer)> second = counter.add printLine(first(6))"
            + " printLine(second(5)) printLine(counter.value) }",
        lines("12", "15", "15"));
  }

  @Test
  void passesFunctionObjectsAndSupportsDeclarationSugar() throws Exception {
    assertOutput(
        "Integer apply(Integer transform(Integer value), Integer input) { return transform(input) }"
            + " class Counter { Integer value Integer add(Integer amount) { return value + amount }"
            + " } Void main() { var doubled = Integer(Integer value) { value * 2 } Counter counter"
            + " = Counter(value: 10) Integer add(Integer amount) = counter.add"
            + " printLine(apply(transform: doubled, input: 6)) printLine(add(5)) }",
        lines("12", "15"));
  }

  @Test
  void dispatchesInterfaceDefaultMethodsThroughTheReceiver() throws Exception {
    assertOutput(
        "interface Incrementable { Integer base() Integer plus(Integer amount) { return this.base()"
            + " + amount } } class Counter implements Incrementable { Integer value public Integer"
            + " base() { return value } } Void main() { Counter counter = Counter(value: 7)"
            + " printLine(counter.plus(5)) }",
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
  void specializesGenericFunctionAndBoundFunctionValues() throws Exception {
    assertOutput(
        "T identity<T>(T value) { return value } "
            + "class Identity { T apply<T>(T value) { return value } } "
            + "Void main() { Function<Integer(Integer)> first = identity "
            + "Identity identityObject = Identity() "
            + "Function<String(String)> second = identityObject.apply "
            + "printLine(first(9)) printLine(second(\"nine\")) }",
        lines("9", "nine"));
  }

  @Test
  void invokesUnboundPrivateMethodDeclarationsWithAnExplicitReceiver() throws Exception {
    assertOutput(
        "class Vault { String value "
            + "private String reveal() { return value } "
            + "public Function<String(Vault)> revealer() { return Vault.reveal.function } } "
            + "Void main() { Vault vault = Vault(value: \"secret\") "
            + "Function<String(Vault)> reveal = vault.revealer() printLine(reveal(vault)) }",
        lines("secret"));
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
    assertEquals(expected, dev.w0fv1.norm.testing.NormTestKit.run(text));
  }

  private static String lines(String... values) {
    return String.join(System.lineSeparator(), values) + System.lineSeparator();
  }
}
