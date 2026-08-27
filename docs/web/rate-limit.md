# 限流

限流保护共享资源和公平性，不能代替身份认证或输入验证。策略由请求键、容量和时间模型组成。

```norm
RateLimitPolicy policy = TokenBucket(
    capacity: 100,
    refill: 10,
    interval: duration(seconds: 1, nanoseconds: 0)
)

RateLimitResult result = limiter.acquire(
    key: principal.id,
    permits: 1
)
```

## 键选择

已认证流量优先使用稳定主体或租户 id；IP 只能作为匿名流量的粗略信号。直接信任客户端提供的转发 Header 会绕过限制，代理链必须显式配置。

## 响应

被拒绝的 HTTP 请求返回 429，并提供可安全公开的 retry-after 信息。内部指标记录策略名和结果，但不能把用户 id 直接作为高基数 metric label。

## 分布式实现

单实例 limiter 只约束本地进程。跨实例限制需要集中存储或一致分片，并明确时钟、网络分区和存储不可用时采用 fail-open 还是 fail-closed。

策略更新应原子生效；已有 bucket 的迁移规则必须确定，避免部署后瞬间清空或翻倍配额。
