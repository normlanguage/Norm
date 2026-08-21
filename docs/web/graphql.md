# GraphQL 使用指南

GraphQL 服务由 schema、resolver、context 和执行限制组成。详细原则见[GraphQL 设计](/web/graphql-design)。

## 建立 Schema

```norm
GraphSchema schema = GraphSchema.builder()
    .object(type: orderType)
    .query(name: "order", resolver: resolveOrder)
    .build()
```

schema 在应用启动时验证。重复字段、无法解析的类型引用和 nullable 不匹配会阻止启动。

## Resolver

```norm
Result<Order, GraphError> resolveOrder(
    OrderArguments arguments,
    GraphContext context
) {
    return context.orders.find(id: arguments.id)
}
```

context 显式提供认证主体、deadline、DataLoader 和应用服务。resolver 不读取全局 request。

## 错误和性能

业务错误返回稳定 code 和允许公开的字段；异常由执行器记录并转换为通用内部错误。每个请求应用深度、复杂度、字段数和超时限制，列表字段需要分页上限。

生产 schema 变更通过导出的 SDL 做兼容性检查，deprecated 字段保留迁移说明和删除日期。

