# OpenAI

[English](README.md)

独立 Norm Module。将本目录放在应用的 `dependencies/openai`，并声明 `dependency(repository: "github", name: "openai", version: 1)`。模块尚未发布到远程仓库。

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

`endpoint` 接受完整 Responses 或 Chat Completions URL。`generate` 使用真实 SSE 流，即时分发文本增量。返回值中的 `text` 是同一回复的完整文本，按需使用，避免重复打印。通过 `response.status`、`refusal`、`errorMessage` 和 `incompleteReason` 检查最终结果。

工具直接使用函数或绑定方法引用：

```norm
import agent.Agent
import openai.Tool
import openai.ToolParameter

@Tool(name: "sum", description: "计算两个整数的和")
Integer sumNumbers(
  @ToolParameter(description: "第一个整数") Integer left,
  Integer right
) {
  return left + right
}

var agent = Agent(apiKey: key, model: model, endpoint: endpoint, tool: [sumNumbers])
var response = agent.run(input: "计算 19 加 23")
```

绑定方法使用 `tools: [service.method]`。重载函数先赋给精确 `Function<R(P...)>` 变量，再传入工具列表。参数默认值、nullable、序列化字段名和返回值类型沿用 Norm 契约；结构化 value 使用 `@Serializable()`。注册时校验工具注解、名称唯一性和参数、结果的序列化能力。工具异常通过 `onToolFailed` 通知，并将错误结果交给模型继续处理。

`Client.generate` 只请求一次模型；`Agent.run` 自动执行工具并继续请求模型。通过 `session: AgentSession()` 保留会话，后续任务传入同一个对象；省略时使用独立会话。`toolProvider` 在每轮请求前提供工具列表，该轮调用共享函数版本与参数契约快照；未通过工具契约校验的目录保留上次接受的工具，并将诊断交给模型修正。`contextProvider` 提供每轮请求前的可选上下文。`beginToolScope` 为一轮工具调用提供作用域，`ToolScope.complete()` 返回的可选文本进入模型上下文，`close()` 在所有退出路径上执行。声明与生命周期以 [agent.norm](agent.norm) 为准。

[自扩展 Agent](../../examples/self_extending_agent/README.md)建立在独立的 Agent 库之上。`Client.exchange`、`ResponseRequest`、`ToolRegistry` 保持协议层 API，供需要自行管理上下文和工具调度的应用使用。

每个事件都有对应的 `onXxx` 参数，同时支持 `onEvent: (GenerationEvent event)`。同时注册时先执行 `onEvent`，再执行专用回调。模型事件由 `generate` 和 `run` 提供；工具执行和整个任务的事件由 `run` 提供。事件与参数类型见 [events.norm](events.norm)，生命周期入口见 [client.norm](client.norm) 和 [agent.norm](agent.norm)。错误通知不吞异常；服务端返回的失败、不完整和拒绝状态保留在 `Response` 中。

`client.generate<Result>(input: ...)` 从 `Result` 推导输出 Schema，结果通过 `response.decode<Result>()` 解码。严格模式接受对象结果，nullable 字段使用必填且可为 null 的表示；动态 Map 无法用于严格模式。仅支持 JSON 对象模式的兼容服务可传 `mode: StructuredOutputMode.JsonObject`。高级请求可用 `structuredOutput<Result>()` 生成同一格式定义。

实现入口：[函数工具](tools.norm)、[JSON Schema 与函数映射](../../stdlib/std/json/json.norm)。运行验收：[OpenAiClientTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/project/OpenAiClientTest.java)、[OpenAiAgentTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/project/OpenAiAgentTest.java)、[JsonFunctionExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/JsonFunctionExecutionTest.java)。协议依据：[OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)。

