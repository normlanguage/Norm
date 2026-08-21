# GraphQL 设计

GraphQL 模块以显式 schema 和 resolver 注册为核心，不通过扫描业务 class 自动暴露字段。

```norm
GraphSchema schema = GraphSchema.builder()
    .query(name = "order", resolver = orderResolver)
    .build()
```

## 类型映射

GraphQL nullable 与 Norm nullable 必须逐层对应：`String!` 映射 `String`，`String` 映射 `String?`，列表本身和列表元素的 nullability 分别保存。无法表达的映射在启动时失败。

## Resolver

resolver 是普通函数，参数包含类型化 parent、arguments 和 request context。业务失败转换为稳定 GraphQL error code；内部异常只记录 trace id，不向客户端泄露 stack。

## 执行限制

服务器必须支持查询深度、复杂度、字段数量和执行超时限制。DataLoader 一类批处理器按请求作用域创建，避免 N+1，同时不能跨用户缓存授权敏感数据。

## Schema 演进

新增字段通常兼容；删除字段、改变字段或参数类型需要弃用周期。生产环境可以关闭任意 introspection，但构建过程仍应导出确定的 schema 供客户端生成和差异检查。

