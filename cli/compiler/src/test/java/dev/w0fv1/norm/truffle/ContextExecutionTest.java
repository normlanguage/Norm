package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class ContextExecutionTest {
  @Test
  void restoresTypedContextsAfterNestedCallsAndFailures() throws Exception {
    assertEquals(
        "context-ok" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.context.currentContext
            import std.context.withContext
            import std.core.Exception
            value Settings<T> { T value }
            Void main() {
              require(condition: currentContext<Settings<String>>() == null, message: "no ambient context")
              var result = withContext(value: Settings<String>("outer"), action: () {
                require(condition: currentContext<Settings<String>>()?.value == "outer", message: "outer context")
                withContext(value: Settings<Integer>(42), action: () {
                  require(condition: currentContext<Settings<Integer>>()?.value == 42, message: "generic identity")
                  require(condition: currentContext<Settings<String>>()?.value == "outer", message: "independent typed contexts")
                })
                try {
                  withContext<Settings<String>, Integer>(value: Settings<String>("inner"), action: () {
                    require(condition: currentContext<Settings<String>>()?.value == "inner", message: "nested context")
                    throw Exception(message: "expected")
                  })
                } catch Exception failure {}
                require(condition: currentContext<Settings<String>>()?.value == "outer", message: "restore after failure")
                7
              })
              require(condition: result == 7, message: "result preserved")
              require(condition: currentContext<Settings<String>>() == null, message: "context released")
              printLine("context-ok")
            }
            """));
  }
}
