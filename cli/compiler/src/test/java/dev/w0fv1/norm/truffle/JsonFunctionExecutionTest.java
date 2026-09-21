package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class JsonFunctionExecutionTest {
  @Test
  void reflectsAnnotationsAndInvokesBoundFunctionsThroughTypedJson() {
    assertEquals(
        "done" + System.lineSeparator(),
        NormTestKit.run(
            """
        import std.annotation.FunctionTarget
        import std.annotation.ParameterTarget
        import std.annotation.RuntimeRetention
        import std.json.functionSchema
        import std.json.invokeJson
        import std.json.writeJson
        import std.json.fromJson
        import std.json.JsonException
        annotation Label implements FunctionTarget, ParameterTarget, RuntimeRetention {
          String description
        }
        class Counter {
          Integer total = 0
          @Label(description: "Add a number")
          Integer add(@Label(description: "Amount") Integer amount) {
            total = total + amount
            return total
          }
        }
        Integer sumNumbers(Integer left, Integer right) { return left + right }
        Void main() {
          List<Function<?>> tools = [sumNumbers]
          require(condition: invokeJson(operation: tools[0], arguments: "{\\\"left\\\":19,\\\"right\\\":23}") == "42", message: "top level function")
          var counter = Counter()
          Function<?> operation = counter.add
          require(condition: operation.annotation<Label>()?.description == "Add a number", message: "function metadata")
          require(condition: operation.parameters()[0].annotation<Label>()?.description == "Amount", message: "parameter metadata")
          var schema = writeJson(value: functionSchema(operation: operation))
          require(condition: schema.contains(value: "amount") && schema.contains(value: "integer"), message: "schema")
          var result = invokeJson(operation: operation, arguments: "{\\\"amount\\\":3}")
          require(condition: fromJson<Integer>(value: result) == 3 && counter.total == 3, message: "bound receiver")
          var rejected = false
          try { invokeJson(operation: operation, arguments: "{\\\"amount\\\":\\\"wrong\\\"}") }
          catch JsonException failure { rejected = true }
          require(condition: rejected && counter.total == 3, message: "validate before execution")
          for String input : ["{}", "{\\\"amount\\\":1,\\\"extra\\\":0}", "{\\\"amount\\\":1,\\\"amount\\\":2}", "{\\\"amount\\\":null}", "[]", "{\\\"amount\\\":1} true"] {
            rejected = false
            try { invokeJson(operation: operation, arguments: input) }
            catch JsonException failure { rejected = true }
            require(condition: rejected && counter.total == 3, message: "invalid arguments cannot execute")
          }
          printLine("done")
        }
        """));
  }
}
