# 后台任务

后台任务用于把不需要在 HTTP 请求内完成的工作提交到持久队列。提交成功只表示任务已被接受，不表示业务动作已经完成。

## 任务定义

```norm
value SendReceipt {
    OrderId orderId
    EmailAddress recipient
}

TaskHandler<SendReceipt> handler = TaskHandler<SendReceipt>(
    name = "send-receipt",
    run = sendReceipt
)
```

任务名称、输入 codec、处理函数和重试策略通过注册表显式绑定，不通过扫描 annotation 发现。

## 交付语义

首版以 at-least-once 为基础：同一任务可能执行多次。handler 必须幂等，或使用 idempotency key 和事务性 outbox 避免重复副作用。

## 重试

错误分为可重试、永久失败和取消。重试策略包含最大次数、指数退避、抖动和总截止时间。达到上限后进入 dead-letter 队列，并保留最后错误和尝试历史。

## 上下文

任务携带稳定 id、创建时间、trace context 和业务 correlation id，但不保存整个 HTTP Request。敏感凭据应在执行时重新获取，不能序列化进任务载荷。

