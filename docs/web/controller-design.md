# Controller 设计

Controller 是传输层适配器：读取 HTTP 输入、调用应用服务并生成 HTTP 响应。它不是语言结构，也不要求继承框架基类。

```norm
class OrderController {
    OrderService orders

    HttpResponse get(HttpRequest request) {
        Result<OrderId, InputError> id = OrderId.parse(
            text = request.path.string(name = "id")
        )

        return responses.fromResult(result = orders.find(id = id))
    }
}
```

## 职责边界

Controller 可以处理解析、认证上下文、内容协商和错误映射。定价、库存、权限资源规则等业务逻辑属于 service 或领域函数。

依赖通过构造器显式传入。共享连接池等资源以 Ref 或具有明确共享语义的库类型出现，不使用全局 service locator。

## 返回值

handler 返回 HttpResponse 或 `Task<HttpResponse>`。预期业务失败通过 Result 映射为稳定状态码；未处理异常由顶层错误 middleware 转成 500 并记录 trace id。

Controller 测试应直接构造 request 和 fake service，无需启动真实 socket。

