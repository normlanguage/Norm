# 日志

日志是结构化事件流。事件由 level、稳定 event name、message、字段、时间和 observation context 构成。

```norm
logger.info(
    event = "iteration.completed",
    message = "iteration completed",
    fields = ["count" = count]
)
```

## 级别

Trace 用于高频诊断，Debug 用于开发信息，Info 表示正常生命周期，Warn 表示可恢复异常状态，Error 表示操作失败。Fatal 是否存在由进程 API 决定，记录日志本身不会自动退出。

## 字段

字段值通过类型化 formatter 编码。Secret、密码、token、cookie 和授权 Header 默认遮蔽。用户 id 等高敏感标识需要项目策略明确允许。

## Context

request id、trace id 和 service version 从显式 ObservationContext 合并。异步任务派生 context，不依赖线程固定不变。

## 输出与背压

console、JSON 文件和远程 exporter 实现统一 Sink。队列有上限；拥塞时按级别丢弃或同步降级，并暴露 dropped event 计数。关闭进程时在 deadline 内 flush。

日志调用失败不能破坏主要业务流程，但初始化配置错误应阻止应用以未知策略启动。

