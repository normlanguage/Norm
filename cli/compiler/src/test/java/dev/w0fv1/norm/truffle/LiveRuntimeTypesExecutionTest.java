package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
final class LiveRuntimeTypesExecutionTest {
  @Test
  void retainedObjectUsesChangedMethodWithoutLosingItsFields() {
    String source =
        """
        class Counter { Integer value = 0; Integer step() { value = value + 1; value } }
        Void main() {
          var counter = Counter()
          var count = 0
          for (count < 3) { printLine(counter.step()); count = count + 1 }
        }
        """;
    assertUpdate(source, source.replace("value + 1", "value + 10"), 1, List.of("1", "11", "21"));
  }

  @Test
  void retainedGenericInstanceKeepsInterfaceWitnessAndTypeArguments() {
    String source =
        """
        interface Processor<T> { T get(); Integer step() }
        class Box<T> implements Processor<T> {
          T value
          Integer progress = 0
          Box(T value) { this.value = value }
          T get() { value }
          Integer step() { progress = progress + 1; progress }
        }
        Void main() {
          Processor<Integer> box = Box<Integer>(19)
          var count = 0
          for (count < 3) { printLine(box.get()); printLine(box.step()); count = count + 1 }
        }
        """;
    assertUpdate(
        source,
        source.replace("progress + 1", "progress + 2"),
        2,
        List.of("19", "1", "19", "3", "19", "5"));
  }

  @Test
  void addedFieldIsInitializedOnceOnTheRetainedObject() {
    String source =
        """
        Integer seed() { printLine("seed"); 10 }
        class Counter { Integer value = 0; Integer step() { value = value + 1; value } }
        Void main() {
          var counter = Counter()
          var alias = counter
          var count = 0
          for (count < 3) { printLine(alias.step()); count = count + 1 }
        }
        """;
    String candidate =
        source
            .replace("class Counter {", "class Counter { Integer extra = seed();")
            .replace("value + 1", "value + extra");
    assertUpdate(source, candidate, 1, List.of("1", "seed", "11", "21"));
  }

  @Test
  void addedGenericFieldUsesTheRetainedReceiverAndReifiedType() {
    String source =
        """
        interface Reader<T> { T get() }
        class Box<T> implements Reader<T> {
          T value
          Box(T value) { this.value = value }
          T get() { value }
        }
        Void main() {
          Reader<Integer> box = Box<Integer>(19)
          var count = 0
          for (count < 3) { printLine(box.get()); count = count + 1 }
        }
        """;
    String candidate =
        source
            .replace("T value\n", "T value\n T cached = value\n")
            .replace("T get() { value }", "T get() { cached }");
    assertUpdate(source, candidate, 1, List.of("19", "19", "19"));
  }

  @Test
  void addedFieldsResolveInitializerDependenciesByStableFieldIdentity() {
    String source =
        """
        class Counter { Integer value = 1; Integer step() { value = value + 1; value } }
        Void main() {
          var counter = Counter()
          var count = 0
          for (count < 3) { printLine(counter.step()); count = count + 1 }
        }
        """;
    String candidate =
        source
            .replace(
                "class Counter {",
                "class Counter { Integer extra = later + value; Integer later = 10;")
            .replace("value + 1", "value + extra");
    assertUpdate(source, candidate, 1, List.of("2", "14", "26"));
  }

  @Test
  void statefulAnnotationRetainsStateAndPairsHooksAcrossConfigurationChanges() {
    String source =
        """
        import std.annotation.FunctionInterceptor
        import std.annotation.RuntimeRetention
        annotation Count implements FunctionInterceptor, RuntimeRetention {
          String label
          private Integer calls = 0
          Void before(FunctionContext context) { calls = calls + 1; printLine("before:" + label + ":" + calls.toString()) }
          Void after(FunctionContext context, FunctionCompletion completion) { printLine("after:" + label + ":" + calls.toString()) }
        }
        @Count(label: "old") Integer work() { printLine("body"); 7 }
        Void main() {
          var count = 0
          for (count < 3) { printLine(work()); count = count + 1 }
        }
        """;
    String candidate =
        source
            .replace("calls + 1", "calls + 10")
            .replace("@Count(label: \"old\")", "@Count(label: \"new\")");
    assertUpdate(
        source,
        candidate,
        1,
        List.of(
            "before:old:1",
            "body",
            "after:old:1",
            "7",
            "before:new:11",
            "body",
            "after:new:11",
            "7",
            "before:new:21",
            "body",
            "after:new:21",
            "7"));
  }

  private static void assertUpdate(
      String source, String candidateSource, int after, List<String> expected) {
    var originalResult = NormTestKit.compile(source);
    assertTrue(originalResult.isSuccess(), originalResult.diagnostics().toString());
    var candidateResult = NormTestKit.compile(candidateSource);
    assertTrue(candidateResult.isSuccess(), candidateResult.diagnostics().toString());
    var live =
        new TruffleExecutionBackend().prepareLive(originalResult.output().orElseThrow().artifact());
    var candidate = candidateResult.output().orElseThrow().artifact();
    var output = new StringBuilder();
    var writer =
        new Writer() {
          private int lines;

          @Override
          public void write(char[] characters, int offset, int length) {
            for (int index = offset; index < offset + length; index++) {
              char character = characters[index];
              output.append(character);
              if (character == '\n' && ++lines == after) live.update(candidate);
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    live.execute(ExecutionContext.of(new PrintWriter(writer)));
    assertEquals(expected, output.toString().lines().toList());
  }
}
