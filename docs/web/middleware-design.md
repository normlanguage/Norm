# Middleware 设计

middleware 包装下一个 handler，用于跨路由的传输层行为。

```norm
interface Middleware {
    HttpResponse handle(HttpRequest request, HttpHandler next)
}

HttpApplication app = HttpApplication(
    router: router,
    middleware: [requestId, tracing, authentication, errors]
)
```

## 顺序

列表顺序就是入站执行顺序，响应按相反方向返回。认证必须在依赖 Principal 的授权之前；错误边界应包住可能抛异常的后续组件。框架不会自动重排。

middleware 可以短路并直接返回响应，例如限流拒绝或 CORS preflight。调用 next 的次数必须为零或一次，多次调用是运行时错误。

## 上下文

请求上下文是类型化键值集合或明确字段，不通过进程全局变量传递。middleware 添加的值只在当前请求及显式派生任务中有效。

业务逻辑不应藏在 middleware。数据库事务、订单权限等需要清晰输入和结果的行为应作为应用服务显式调用。

