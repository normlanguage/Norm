# HTTP API

HTTP 模块提供强类型请求、流式响应和客户端 adapter。公开签名以 [`client.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/http/client.norm) 与 [`json.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/http/json.norm) 为准。HTTP server、路由、Controller 和 Authentication 尚未交付。

## 客户端

```norm
HttpRequest request = get(uri: Uri(value: "https://example.com/status"))
HttpResponse response = systemHttpClient().send(
  request: request,
  timeout: duration(seconds: 5, nanoseconds: 0)
)
String body = use<String>(resource: response, body: () {
  decodeText(
    content: readAll(reader: response, maximumBytes: 1048576),
    encoding: TextEncoding.Utf8
  )
})
```

`HttpResponse` 同时实现 `ByteReader` 和 `Resource`。传给 `send` 的 timeout 是覆盖发送、等待响应和读取 body 的总预算，execution 的取消状态会传播到请求和 body 读取。响应必须通过 `use` 或显式 `close()` 确定性关闭。默认不跟随重定向。

## 服务器

服务器 adapter 尚未进入标准库。它将复用相同的 `HttpRequest`、header、body stream 和异常模型。

## 类型

`HttpMethod`、`Status`、`HeaderMap` 和 `Uri` 不暴露宿主类型。`HeaderMap` 保留重复 header；HTTP status 是正常响应数据。

JDK adapter 使用 `java.net.http.HttpClient`，DNS、连接、TLS、协议、超时、取消和 body I/O 失败统一抛出 `HttpException`，不使用 `Result`。

JSON 请求使用 `postJson` 或 `jsonRequest`。它们在 `Bytes` 边界组合 `std.json`，统一写入 `application/json`；响应读取上限和 `decodeJson<T>` 仍由调用方显式控制。
