# HTTP API

HTTP 模块提供协议基础类型和客户端/服务器 adapter。路由、Controller 和 Authentication 属于 Web 平台，不是语言语法。

## 客户端

```norm
HttpRequest request = HttpRequest.get(uri: Uri("https://example.com/status"))
HttpResponse response = client.send(
    request: request,
    timeout: duration(seconds: 5, nanoseconds: 0)
)
```

连接、读取和总请求 deadline 分开配置。默认跟随重定向的范围、最大次数和跨域凭据剥离规则必须明确。body 是可关闭的流，便利读取 API 设置大小上限。

## 服务器

服务器 adapter 把连接转换为 HttpRequest，并发送 handler 产生的 HttpResponse。request line、header、body 和空闲时间都有限制。优雅关闭停止接收新连接并等待有限时间。

## 类型

Method 与 Status 是受控值类型，HeaderMap 支持重复值，Uri 区分 path、query 与 fragment。Cookie 使用专门 parser/builder，不能作为普通 Header 字符串拼接。

首版可以复用成熟 HTTP runtime，但 Norm 层必须保持相同的超时、资源关闭和错误模型。

DNS、连接、TLS、协议解析、超时与取消失败抛出 `HttpException`。HTTP status 是正常响应数据，不作为异常或 Result 分支。
