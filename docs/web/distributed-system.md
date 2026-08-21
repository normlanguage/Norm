# 分布式系统边界

分布式能力建立在 HTTP、消息、存储和可观测性库之上，不向语言增加“远程对象”语法。一次远程调用必须在类型和配置中保留超时、失败与重试事实。

## 调用模型

```norm
Result<Order, RemoteError> order = client.getOrder(
    id: id,
    deadline: context.deadline,
    cancellation: context.cancellation
)
```

远程调用不能与本地函数共享相同的无失败签名。`RemoteError` 至少区分超时、连接失败、协议错误、远端拒绝和取消。

## 重试与幂等

只有明确可重试的操作才能自动重试。写操作需要 idempotency key，退避包含随机抖动，并受总 deadline 限制。重试次数不是可靠性指标；端到端成功率和尾延迟更重要。

## 一致性

平台不提供透明分布式事务。跨服务业务流程使用 outbox、幂等消费、补偿动作或 saga，并在领域模型中保留中间状态。

## 故障隔离

连接池、并发上限、熔断和 bulkhead 按下游依赖配置。fallback 必须返回语义正确的显式结果，不能用空列表或 null 掩盖依赖故障。

服务发现、负载均衡和 trace context 由 adapter 提供，但最终 endpoint 选择和策略必须可观测、可测试。

