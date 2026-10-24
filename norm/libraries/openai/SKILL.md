# openai Skill

Use this skill to work with Norm module `openai` version 1. Use only declarations listed below.

## `openai`

Source: `client.norm`

### `ResponseStatus`

Public API declaration ResponseStatus.

```norm
enum ResponseStatus
```

#### `Completed`

```norm
Completed
```

#### `Incomplete`

```norm
Incomplete
```

#### `Failed`

```norm
Failed
```

#### `Cancelled`

```norm
Cancelled
```

#### `Queued`

```norm
Queued
```

#### `InProgress`

```norm
InProgress
```

### `OpenAiException`

Public API declaration OpenAiException.

```norm
class OpenAiException extends Exception
```

#### `code`

Public field code.

```norm
String code
```

Type: `String`

#### `httpStatus`

Public field httpStatus.

```norm
Integer? httpStatus
```

Type: `Integer?`

#### `requestId`

Public field requestId.

```norm
String? requestId
```

Type: `String?`

#### `OpenAiException`

Creates an OpenAI protocol exception.

```norm
OpenAiException(String code, String message, Integer? httpStatus, String? requestId)
```

Parameters:

- `code`: `String`
The error code.

- `message`: `String`
The error message.

- `httpStatus`: `Integer?`
The HTTP status.

- `requestId`: `String?`
The request identifier.


### `StructuredOutputMode`

Public API declaration StructuredOutputMode.

```norm
enum StructuredOutputMode
```

#### `JsonSchema`

```norm
JsonSchema
```

#### `JsonObject`

```norm
JsonObject
```

### `StructuredOutput`

Public API declaration StructuredOutput.

```norm
value StructuredOutput
```

#### `name`

Public field name.

```norm
String name
```

Type: `String`

#### `schema`

Public field schema.

```norm
JsonValue schema
```

Type: `JsonValue`

#### `mode`

Public field mode.

```norm
StructuredOutputMode mode
```

Type: `StructuredOutputMode`

### `structuredOutput`

Public API declaration structuredOutput.

```norm
StructuredOutput structuredOutput<T>(String name, StructuredOutputMode mode)
```

Type parameters:

- `T`

Parameters:

- `name`: `String`
The output name.

- `mode`: `StructuredOutputMode`
The output mode.


Returns: `StructuredOutput`

### `ResponseInput`

Public API declaration ResponseInput.

```norm
enum ResponseInput
```

#### `Text`

```norm
Text(String value)
```

Parameters:

- `value`: `String`

#### `Items`

```norm
Items(List<JsonValue> values)
```

Parameters:

- `values`: `List<JsonValue>`

### `ToolDefinition`

Public API declaration ToolDefinition.

```norm
value ToolDefinition
```

#### `name`

Public field name.

```norm
String name
```

Type: `String`

#### `description`

Public field description.

```norm
String description
```

Type: `String`

#### `parameters`

Public field parameters.

```norm
JsonValue parameters
```

Type: `JsonValue`

#### `strict`

Public field strict.

```norm
Boolean strict
```

Type: `Boolean`

### `ToolCall`

Public API declaration ToolCall.

```norm
value ToolCall
```

#### `callId`

Public field callId.

```norm
String callId
```

Type: `String`

#### `name`

Public field name.

```norm
String name
```

Type: `String`

#### `arguments`

Public field arguments.

```norm
String arguments
```

Type: `String`

### `ResponseRequest`

Public API declaration ResponseRequest.

```norm
value ResponseRequest
```

#### `model`

Public field model.

```norm
String model
```

Type: `String`

#### `input`

Public field input.

```norm
ResponseInput input
```

Type: `ResponseInput`

#### `instructions`

Public field instructions.

```norm
String? instructions
```

Type: `String?`

#### `format`

Public field format.

```norm
StructuredOutput? format
```

Type: `StructuredOutput?`

#### `maximumOutputTokens`

Public field maximumOutputTokens.

```norm
Integer maximumOutputTokens
```

Type: `Integer`

#### `tools`

Public field tools.

```norm
List<ToolDefinition> tools
```

Type: `List<ToolDefinition>`

### `Usage`

Public API declaration Usage.

```norm
value Usage
```

#### `inputTokens`

Public field inputTokens.

```norm
Long inputTokens
```

Type: `Long`

#### `outputTokens`

Public field outputTokens.

```norm
Long outputTokens
```

Type: `Long`

#### `totalTokens`

Public field totalTokens.

```norm
Long totalTokens
```

Type: `Long`

### `Response`

Public API declaration Response.

```norm
value Response
```

#### `id`

Public field id.

```norm
String id
```

Type: `String`

#### `status`

Public field status.

```norm
ResponseStatus status
```

Type: `ResponseStatus`

#### `text`

Public field text.

```norm
String text
```

Type: `String`

#### `refusal`

Public field refusal.

```norm
String? refusal
```

Type: `String?`

#### `incompleteReason`

Public field incompleteReason.

```norm
String? incompleteReason
```

Type: `String?`

#### `usage`

Public field usage.

```norm
Usage? usage
```

Type: `Usage?`

#### `errorCode`

Public field errorCode.

```norm
String? errorCode
```

Type: `String?`

#### `errorMessage`

Public field errorMessage.

```norm
String? errorMessage
```

Type: `String?`

#### `requestId`

Public field requestId.

```norm
String? requestId
```

Type: `String?`

#### `output`

Public field output.

```norm
List<JsonValue> output
```

Type: `List<JsonValue>`

#### `calls`

Public field calls.

```norm
List<ToolCall> calls
```

Type: `List<ToolCall>`

#### `decode`

Public API declaration decode.

```norm
T decode<T>()
```

Type parameters:

- `T`

Returns: `T`

### `Client`

Public API declaration Client.

```norm
class Client
```

#### `Client`

Creates an OpenAI protocol client.

```norm
Client(String apiKey, String model, String endpoint, HttpClient transport, Duration timeout, Integer maximumResponseBytes)
```

Parameters:

- `apiKey`: `String`
The API key.

- `model`: `String`
The model name.

- `endpoint`: `String`
The endpoint URL.

- `transport`: `HttpClient`
The HTTP transport.

- `timeout`: `Duration`
The request timeout.

- `maximumResponseBytes`: `Integer`
The response byte limit.


#### `defaultModel`

Public API declaration defaultModel.

```norm
String defaultModel()
```

Returns: `String`

#### `generate`

Public API declaration generate.

```norm
Response generate(String input, String? instructions, List<Function<?>> tools, Integer maximumOutputTokens, Function<Void(String)>? onResponseStarted, Function<Void(String)>? onMessageStarted, Function<Void(String)>? onText, Function<Void(Message)>? onMessageCompleted, Function<Void(ToolCall)>? onToolCallReceived, Function<Void(Response)>? onResponseCompleted, Function<Void(Exception)>? onFailed, Function<Void(GenerationEvent)>? onEvent)
```

Parameters:

- `input`: `String`
The input.

- `instructions`: `String?`
The instructions.

- `tools`: `List<Function<?>>`
The tools.

- `maximumOutputTokens`: `Integer`
The output token limit.

- `onResponseStarted`: `Function<Void(String)>?`
The response-start callback.

- `onMessageStarted`: `Function<Void(String)>?`
The message-start callback.

- `onText`: `Function<Void(String)>?`
The text callback.

- `onMessageCompleted`: `Function<Void(Message)>?`
The message callback.

- `onToolCallReceived`: `Function<Void(ToolCall)>?`
The tool-call callback.

- `onResponseCompleted`: `Function<Void(Response)>?`
The response callback.

- `onFailed`: `Function<Void(Exception)>?`
The failure callback.

- `onEvent`: `Function<Void(GenerationEvent)>?`
The event callback.


Returns: `Response`

#### `generate`

Public API declaration generate.

```norm
Response generate<T>(String input, String? instructions, Integer maximumOutputTokens, StructuredOutputMode mode, Function<Void(String)>? onResponseStarted, Function<Void(String)>? onMessageStarted, Function<Void(String)>? onText, Function<Void(Message)>? onMessageCompleted, Function<Void(ToolCall)>? onToolCallReceived, Function<Void(Response)>? onResponseCompleted, Function<Void(Exception)>? onFailed, Function<Void(GenerationEvent)>? onEvent)
```

Type parameters:

- `T`

Parameters:

- `input`: `String`
The input.

- `instructions`: `String?`
The instructions.

- `maximumOutputTokens`: `Integer`
The output token limit.

- `mode`: `StructuredOutputMode`
The output mode.

- `onResponseStarted`: `Function<Void(String)>?`
The response-start callback.

- `onMessageStarted`: `Function<Void(String)>?`
The message-start callback.

- `onText`: `Function<Void(String)>?`
The text callback.

- `onMessageCompleted`: `Function<Void(Message)>?`
The message callback.

- `onToolCallReceived`: `Function<Void(ToolCall)>?`
The tool-call callback.

- `onResponseCompleted`: `Function<Void(Response)>?`
The response callback.

- `onFailed`: `Function<Void(Exception)>?`
The failure callback.

- `onEvent`: `Function<Void(GenerationEvent)>?`
The event callback.


Returns: `Response`

#### `exchange`

Public API declaration exchange.

```norm
Response exchange(ResponseRequest request, GenerationCallbacks callbacks, Boolean stream)
```

Parameters:

- `request`: `ResponseRequest`
The response request.

- `callbacks`: `GenerationCallbacks`
The callbacks.

- `stream`: `Boolean`
Whether to stream events.


Returns: `Response`

## `openai`

Source: `events.norm`

### `Message`

Public API declaration Message.

```norm
value Message
```

#### `id`

The message identifier.

```norm
String id
```

Type: `String`

#### `text`

The rendered message text.

```norm
String text
```

Type: `String`

#### `refusal`

The refusal text, when present.

```norm
String? refusal
```

Type: `String?`

### `ToolResult`

Public API declaration ToolResult.

```norm
value ToolResult
```

#### `call`

The completed tool call.

```norm
ToolCall call
```

Type: `ToolCall`

#### `output`

The serialized tool result.

```norm
String output
```

Type: `String`

### `ToolFailure`

Public API declaration ToolFailure.

```norm
value ToolFailure
```

#### `call`

The failed tool call.

```norm
ToolCall call
```

Type: `ToolCall`

#### `error`

The tool failure.

```norm
Exception error
```

Type: `Exception`

### `GenerationEvent`

Public API declaration GenerationEvent.

```norm
enum GenerationEvent
```

#### `ResponseStarted`

```norm
ResponseStarted(String id)
```

Parameters:

- `id`: `String`

#### `MessageStarted`

```norm
MessageStarted(String id)
```

Parameters:

- `id`: `String`

#### `Text`

```norm
Text(String text)
```

Parameters:

- `text`: `String`

#### `MessageCompleted`

```norm
MessageCompleted(Message message)
```

Parameters:

- `message`: `Message`

#### `ToolCallReceived`

```norm
ToolCallReceived(ToolCall call)
```

Parameters:

- `call`: `ToolCall`

#### `ToolStarted`

```norm
ToolStarted(ToolCall call)
```

Parameters:

- `call`: `ToolCall`

#### `ToolCompleted`

```norm
ToolCompleted(ToolResult result)
```

Parameters:

- `result`: `ToolResult`

#### `ToolFailed`

```norm
ToolFailed(ToolFailure failure)
```

Parameters:

- `failure`: `ToolFailure`

#### `ResponseCompleted`

```norm
ResponseCompleted(Response response)
```

Parameters:

- `response`: `Response`

#### `Failed`

```norm
Failed(Exception error)
```

Parameters:

- `error`: `Exception`

#### `RunStarted`

```norm
RunStarted(String input)
```

Parameters:

- `input`: `String`

#### `RunCompleted`

```norm
RunCompleted(Response response)
```

Parameters:

- `response`: `Response`

### `GenerationCallbacks`

Public API declaration GenerationCallbacks.

```norm
class GenerationCallbacks
```

#### `onResponseStarted`

Receives response start notifications.

```norm
Function<Void(String)>? onResponseStarted
```

Type: `Function<Void(String)>?`

#### `onMessageStarted`

Receives message start notifications.

```norm
Function<Void(String)>? onMessageStarted
```

Type: `Function<Void(String)>?`

#### `onText`

Receives streamed text notifications.

```norm
Function<Void(String)>? onText
```

Type: `Function<Void(String)>?`

#### `onMessageCompleted`

Receives completed message notifications.

```norm
Function<Void(Message)>? onMessageCompleted
```

Type: `Function<Void(Message)>?`

#### `onToolCallReceived`

Receives tool call notifications.

```norm
Function<Void(ToolCall)>? onToolCallReceived
```

Type: `Function<Void(ToolCall)>?`

#### `onToolStarted`

Receives tool start notifications.

```norm
Function<Void(ToolCall)>? onToolStarted
```

Type: `Function<Void(ToolCall)>?`

#### `onToolCompleted`

Receives completed tool notifications.

```norm
Function<Void(ToolResult)>? onToolCompleted
```

Type: `Function<Void(ToolResult)>?`

#### `onToolFailed`

Receives failed tool notifications.

```norm
Function<Void(ToolFailure)>? onToolFailed
```

Type: `Function<Void(ToolFailure)>?`

#### `onResponseCompleted`

Receives completed response notifications.

```norm
Function<Void(Response)>? onResponseCompleted
```

Type: `Function<Void(Response)>?`

#### `onFailed`

Receives generation failure notifications.

```norm
Function<Void(Exception)>? onFailed
```

Type: `Function<Void(Exception)>?`

#### `onRunStarted`

Receives run start notifications.

```norm
Function<Void(String)>? onRunStarted
```

Type: `Function<Void(String)>?`

#### `onRunCompleted`

Receives run completion notifications.

```norm
Function<Void(Response)>? onRunCompleted
```

Type: `Function<Void(Response)>?`

#### `onEvent`

Receives generation event notifications.

```norm
Function<Void(GenerationEvent)>? onEvent
```

Type: `Function<Void(GenerationEvent)>?`

#### `emit`

Public API declaration emit.

```norm
Void emit(GenerationEvent event)
```

Parameters:

- `event`: `GenerationEvent`
The event to emit.


Returns: `Void`

## `openai`

Source: `tools.norm`

### `Tool`

Public API declaration Tool.

```norm
annotation Tool implements FunctionTarget, RuntimeRetention
```

#### `description`

The tool or parameter description.

```norm
String description
```

Type: `String`

#### `name`

The optional exposed tool name.

```norm
String? name
```

Type: `String?`

### `ToolParameter`

Public API declaration ToolParameter.

```norm
annotation ToolParameter implements ParameterTarget, RuntimeRetention
```

#### `description`

The tool or parameter description.

```norm
String description
```

Type: `String`

### `ToolCatalog`

Public API declaration ToolCatalog.

```norm
class ToolCatalog
```

#### `refresh`

Public API declaration refresh.

```norm
ToolRegistry refresh(List<Function<?>> tools)
```

Parameters:

- `tools`: `List<Function<?>>`
The tool functions.


Returns: `ToolRegistry`

#### `rejection`

Public API declaration rejection.

```norm
String? rejection()
```

Returns: `String?`

### `ToolRegistry`

Public API declaration ToolRegistry.

```norm
class ToolRegistry
```

#### `ToolRegistry`

Creates a tool registry.

```norm
ToolRegistry(List<Function<?>> tools)
```

Parameters:

- `tools`: `List<Function<?>>`
The tool functions.


#### `definitions`

Public API declaration definitions.

```norm
List<ToolDefinition> definitions()
```

Returns: `List<ToolDefinition>`

#### `invoke`

Public API declaration invoke.

```norm
String invoke(ToolCall call)
```

Parameters:

- `call`: `ToolCall`
The tool call.


Returns: `String`

