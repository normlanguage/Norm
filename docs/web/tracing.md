# 分布式追踪

Trace 描述一次操作跨进程和异步边界的因果关系。平台采用兼容 W3C Trace Context 的传播格式，业务代码通过类型化 context 使用它。

```norm
Span span = tracer.startSpan(
    name: "orders.load",
    parent: context.trace,
    kind: SpanKind.Internal
)

try {
    loadOrder(id: id)
} catch Exception error {
    span.record(error: error)
    throw error
} finally {
    span.end()
}
```

## Span 命名

名称描述稳定操作，如 `HTTP GET /orders/{id}` 或 `db.query orders`，不能包含用户 id。动态数据放入属性，并经过敏感字段过滤。

## 传播

HTTP、消息队列和后台任务 adapter 负责注入与提取 trace context。格式错误或不受信任的入站 baggage 被忽略或限制大小，不能让外部请求任意制造内部高基数数据。

## 采样与导出

采样决策尽量在 trace 根部完成并向下游传播。错误可触发本地保留策略，但不能保证恢复已经丢弃的上游 span。Exporter 异步批量发送，关闭服务时在有限超时内 flush。

