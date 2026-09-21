# OpenAI client

独立 Norm Module，仅依赖标准库 HTTP、JSON、时间和资源协议。公开类型和默认值以 [client.norm](client.norm) 为准；模块入口见 [module.norm](module.norm)。

将此目录作为应用的 `dependencies/openai`，并在应用模块声明中加入 `dependency(repository: "github", name: "openai", version: 1)`。这是仓库内源码模块，尚未发布到远程模块仓库。

```norm
import openai.Client
import openai.ResponseInput
import openai.ResponseRequest
import std.application.environmentVariable
import std.http.Uri

Void main() {
  Client client = Client(
    apiKey: environmentVariable(name: "OPENAI_API_KEY") ?? "",
    endpoint: Uri(value: "https://api.deepseek.com/responses")
  )
  var response = client.create(request: ResponseRequest(
    model: "deepseek-flash", input: ResponseInput.Text(value: "Say hello.")
  ))
  printLine(response.text)
}
```

`endpoint` 是完整 API URL，默认指向 OpenAI Responses。以 `/chat/completions` 结尾时使用 Chat Completions 协议；两种协议共用请求、响应与函数调用类型。Chat 的文本与工具参数增量分别处理，`reasoning_content` 随本次上下文保留。模型由调用方指定。

需要结构化结果时传入 `StructuredOutput(name:, schema:)`，再用 `response.decode<T>()` 映射到带 `@Serializable()` 的 value。解码前检查完成状态和拒绝信息；业务约束由应用验证。`mode: StructuredOutputMode.JsonObject` 使用 JSON 对象模式，并将同一 schema 加入请求指令；服务端不保证符合 schema，调用方须通过严格解码和业务校验检查结果。默认 `JsonSchema` 使用服务端严格结构化输出。

客户端不保存对话，不自动重试。HTTP 与协议错误使用 `OpenAiException`；网络、编码和有界读取错误保留标准库异常。响应保留完成状态、拒绝、不完整原因、用量和服务端请求 ID。提供文本、结构化输出与函数工具调用。`create(request:, onText:)` 在提供文本回调时发送 `stream: true`，按 SSE 事件立即回调增量，完成后返回统一的 Response；未提供回调时读取普通 JSON。流缺少终止事件、返回错误或超过预算时抛出异常，不降级为完整回复。`ResponseInput.Items` 接受会话历史；`FunctionTool` 定义工具，响应的 `calls` 提供强类型调用，`output` 保留包括 reasoning 在内的原始输出项。调用方负责执行工具、加入 `function_call_output` 并继续请求。客户端不执行工具或保存历史。

协议依据：[OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)、[DeepSeek Responses 兼容说明](https://api-docs.deepseek.com/guides/responses_api/)。真实 HTTP 验收见 [OpenAiClientTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/project/OpenAiClientTest.java)。
