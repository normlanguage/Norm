package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class CollectionExpressionTest {
  @Test
  void spreadsUserIterablesAndReifiedGenericValues() throws Exception {
    var result =
        NormTestKit.run(
            """
            import std.core.Iterable
            import std.core.Iterator
            class Cursor implements Iterator<Integer> {
              Integer current
              Integer end
              Boolean hasNext() { current < end }
              Integer next() { var value = current; current = current + 1; value }
            }
            class Values implements Iterable<Integer> {
              Integer end
              Iterator<Integer> iterator() { Cursor(current: 0, end: end) }
            }
            List<T> collect<T>(Iterable<T> values) { [...values] }
            Void main() {
              for value : collect(Values(end: 3)) { printLine(value) }
              List<String> words = ["hello", "world"]
              for word : collect(words) { printLine(word) }
              List<Integer> empty = [...[], for (Integer value : []) value]
              printLine(empty.size())
            }
            """);
    assertEquals("0\n1\n2\nhello\nworld\n0\n", result.replace("\r\n", "\n"));
  }

  @Test
  void composesConditionalRepeatedAndSpreadElementsInSourceOrder() throws Exception {
    var result =
        NormTestKit.run(
            """
            class Trace {
              Integer calls = 0
              Integer next() { calls = calls + 1; calls }
            }
            Void main() {
              var trace = Trace()
              List<Integer> values = [
                trace.next(),
                if (false) trace.next(),
                if (true) trace.next() else 99,
                for (item : [10, 20]) ...[item, trace.next()],
                ...[40, 50]
              ]
              for value : values { printLine(value) }
              printLine(trace.calls)
            }
            """);
    assertEquals("1\n2\n10\n3\n20\n4\n40\n50\n4\n", result.replace("\r\n", "\n"));
  }

  @Test
  void preservesPerIterationCapturesAndConditionalNarrowing() throws Exception {
    var result =
        NormTestKit.run(
            """
            Void main() {
              List<String?> names = ["first", null, "last"]
              List<Function<String()>> readers = [
                for (name : names)
                  if (name != null) () { name }
              ]
              for read : readers { printLine(read()) }
              List<Integer> source = []
              List<Integer> empty = [if (false) 1, for (value : source) value]
              printLine(empty.size())
            }
            """);
    assertEquals("first\nlast\n0\n", result.replace("\r\n", "\n"));
  }

  @Test
  void materializesBothDefaultArraysAndExpectedLists() throws Exception {
    var result =
        NormTestKit.run(
            """
            T identity<T>(T value) { value }
            Void main() {
              var array = [for (value : [1, 2]) value * 2]
              List<Integer> list = [0, ...array, if (true) 6]
              list.add(8)
              printLine(array.size())
              for value : identity(list) { printLine(value) }
            }
            """);
    assertEquals("2\n0\n2\n4\n6\n8\n", result.replace("\r\n", "\n"));
  }
}
