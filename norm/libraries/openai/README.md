# OpenAI

[简体中文](README.zh-CN.md)

This is an independent Norm module. Put this directory at `dependencies/openai` in an application and declare `dependency(repository: "github", name: "openai", version: 1)`. The module has not yet been published to a remote repository.

```norm
import openai.Client
import openai.ResponseStatus
import std.io.print

var client = Client(apiKey: key, model: model, endpoint: endpoint)
var response = client.generate(
  input: "解释一下 Git 暂存区",
  onText: (String text) { print(text: text) }
)
```

`endpoint` accepts a complete Responses or Chat Completions URL. `generate` consumes a real SSE stream and dispatches text increments immediately. The returned `text` contains the complete text of the same response; use it only as needed to avoid printing twice. Inspect `response.status`, `refusal`, `errorMessage`, and `incompleteReason` for the final outcome.

Tools use functions or bound method references directly:

```norm
import openai.Agent
import openai.Tool
import openai.ToolParameter

@Tool(name: "sum", description: "计算两个整数的和")
Integer sumNumbers(
  @ToolParameter(description: "第一个整数") Integer left,
  Integer right
) {
  return left + right
}

var agent = Agent(client: client, tools: [sumNumbers])
var response = agent.run(input: "计算 19 加 23")
```

Pass bound methods as `tools: [service.method]`. First assign an overloaded function to a variable with the exact `Function<R(P...)>` type, then add it to the tool list. Parameter defaults, nullability, serialized field names, and return types follow Norm's contracts; structured values use `@Serializable()`. Registration checks tool annotations, unique names, and whether parameters and results can be serialized. Tool exceptions notify `onToolFailed`, and the error result is passed back to the model so it can continue.

`Client.generate` makes one model request. `Agent.run` executes tools automatically and then continues requesting the model. Each `run` creates a fresh context; no session is retained. `Client.exchange`, `ResponseRequest`, and `ToolRegistry` serve applications that manage their own context and tool scheduling.

Each event has a corresponding `onXxx` parameter and also supports `onEvent: (GenerationEvent event)`. When both are registered, `onEvent` runs before the specialized callback. `generate` and `run` provide model events; `run` also provides tool execution and whole-task events. See [events.norm](events.norm) for event and parameter types, and [client.norm](client.norm) and [agent.norm](agent.norm) for lifecycle entry points. Error notification does not swallow exceptions. Server-reported failure, incomplete, and refusal states remain in `Response`.

`client.generate<Result>(input: ...)` derives an output schema from `Result`, decoded through `response.decode<Result>()`. Strict mode accepts object results; a nullable field is represented as required but nullable. Dynamic maps cannot be used in strict mode. For compatible services that only support JSON-object mode, pass `mode: StructuredOutputMode.JsonObject`. Advanced requests can use `structuredOutput<Result>()` to generate the same format definition.

Implementation entry points: [function tools](tools.norm) and [JSON Schema and function mapping](../../stdlib/std/json/json.norm). Acceptance: [OpenAiClientTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/project/OpenAiClientTest.java), [OpenAiAgentTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/project/OpenAiAgentTest.java), and [JsonFunctionExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/JsonFunctionExecutionTest.java). Protocol source: [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs).
