# Web 框架规范草案

本页定义 Web 平台的共同边界。具体服务器、数据库和云厂商能力由 adapter 实现，不能改变 handler 的语言语义。

## 应用构造

```norm
HttpApplication application = HttpApplication(
    router = buildRouter(services = services),
    middleware = buildMiddleware(services = services),
    limits = HttpLimits.defaults()
)

application.serve(address = SocketAddress.any(port = 8080))
```

所有组件从 main 或明确工厂构造。启动时完成配置验证、路由冲突检查和资源连接；失败时不进入部分可用状态。

## Handler

同步 handler 类型为 `HttpResponse(HttpRequest request)`，异步版本返回标准 Task。Request body 是有大小限制的单次流，Response header 一旦提交不能修改。

## Middleware

Middleware 接收 request 与 next handler。列表顺序决定嵌套顺序，可用于 request id、日志、trace、认证、压缩和错误边界。它最多调用 next 一次。

## 认证与授权

Authenticator 建立 Principal，Authorizer 根据 principal、action 和 resource 返回明确决定。Session、token 与 OAuth 是 adapter，不改变核心接口。

## 事务

事务是显式函数边界：

```norm
Result<Order, OrderError> saved = database.transaction(
    action = Result<Order, OrderError>(Connection connection) {
        return orders.save(connection = connection, order = order)
    }
)
```

框架不通过 annotation 隐式包裹调用，也不把 Result 失败自动变成 rollback；回滚规则由 transaction API 明确规定。

## 验证

请求 codec 负责结构解析，`Validator<T>` 负责领域前置条件。错误包含稳定 code、结构化 path 和参数，Controller 决定如何映射为 400 或 422。

## 关闭

服务收到终止信号后停止接受连接，在 deadline 内等待请求和任务，关闭资源并 flush 可观测数据。每一步都必须有上限。

