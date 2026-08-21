# Request 与 Response

HttpRequest 是一次入站请求的只读视图，包含 method、URI、header、body stream、远端地址和 request context。

```norm
String id = request.path.string(name = "id")
Option<String> filter = request.query.first(name = "filter")
Result<CreateOrder, DecodeError> body = request.json(codec = createOrderCodec)
```

body 默认是单次读取流。便利 decoder 可以缓冲小 body，但必须受最大字节数限制。query 与 header 都可能重复，API 不用单一 String 静默丢值。

## Response

```norm
HttpResponse response = HttpResponse.json(
    status = HttpStatus.Ok,
    value = order,
    codec = orderCodec
)
```

Response 在发送 header 后进入 committed 状态，之后不能修改状态码。流式 body 的错误无法再改成 JSON 500，只能终止连接并记录错误。

## Header 与 Cookie

header 名大小写不敏感，值保持原始字节语义。Set-Cookie 使用类型化 builder，SameSite、Secure、HttpOnly、Path 和过期时间都显式配置。

框架自动处理 Content-Length 与分块传输的互斥，但内容类型和字符编码由 response 构造器明确指定。

