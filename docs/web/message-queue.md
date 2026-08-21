# 消息队列

消息队列 API 抽象发布、消费、确认和失败处理，但不会假装不同 broker 拥有完全相同的事务与顺序保证。

## 消息信封

```norm
value Message<T> {
    MessageId id
    String topic
    Instant createdAt
    Map<String, String> headers
    T body
}
```

body 使用显式 `Codec<T>` 编解码。类型名、schema 版本和 content type 放在稳定 header 中；未知版本进入错误处理流程，不能强行按最新版解析。

## 发布与消费

```norm
Result<PublishReceipt, QueueError> receipt = producer.publish(
    message: message
)

consumer.subscribe(
    topic: "orders.created",
    handler: handleOrderCreated
)
```

handler 成功后才确认消息。可重试失败执行 backoff，永久失败进入 dead-letter topic。取消或进程关闭时停止拉取新消息，并给在途任务一个有限完成期限。

## 保证边界

默认文档只承诺 at-least-once。全局顺序、恰好一次和跨数据库事务不能作为通用 API 承诺；需要这些性质时必须选择具体 adapter，并配合 outbox、幂等键或 broker 事务。

