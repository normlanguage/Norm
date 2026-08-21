# REST API 设计

REST 模块把 HTTP 方法、路径、Header 和 body 映射为显式函数契约。路由通过代码注册，不依赖 `@Get` 一类 annotation。

```norm
router.get(
    path = "/orders/{id}",
    handler = getOrder
)

HttpResponse getOrder(HttpRequest request) {
    OrderId id = OrderId.parse(text = request.path.string(name = "id"))
    return orders.find(id = id)
}
```

## 资源与方法

路径使用名词和稳定标识符。GET、HEAD 是安全方法；PUT 和 DELETE 应保持幂等；POST 用于创建或非幂等命令。不能只因 handler 名称不同就忽略 HTTP 方法语义。

## 状态码

- 200：成功并返回表示；
- 201：创建成功，并提供 Location；
- 204：成功且没有 body；
- 400：请求结构错误；
- 401/403：未认证/无权限；
- 404：资源不存在；
- 409：当前资源状态冲突；
- 422：结构有效但领域验证失败。

## 错误格式

错误响应包含稳定 `code`、人类消息、字段问题和 trace id。异常 stack、SQL 或内部类型名不进入响应。

## 并发更新

需要防止覆盖时使用 ETag 与 `If-Match`，或在领域命令中携带版本。分页必须固定排序，cursor 编码不暴露可篡改内部状态。

