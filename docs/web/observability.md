# 可观测性

可观测性由日志、指标和 trace 三类信号组成。三者共享 service、environment、version 和 correlation context，但各自承担不同问题。

## 请求上下文

```norm
ObservationContext context = ObservationContext(
    trace = request.traceContext,
    service = serviceInfo,
    attributes = baseAttributes
)
```

上下文沿显式 task 和消息边界传播。库不能依赖无法检查的全局 thread-local 作为唯一来源，因为异步任务可能切换线程。

## 结构化日志

日志事件使用稳定名称和字段，不拼接机器需要解析的长字符串。token、密码、cookie 和个人敏感数据默认禁止记录。错误日志包含异常类型和 stack，但对客户端响应隐藏内部细节。

## 指标

指标用于聚合趋势，label 集合必须低基数。request id、用户 id 和完整 URL 不可作为 label；它们属于日志或 trace 属性。

## Trace

每个入站请求建立或继续 trace。数据库、外部 HTTP 与队列操作创建子 span。采样决定是否导出详细 trace，但 trace id 仍可用于关联日志。

导出器故障不能阻塞核心业务。缓冲区必须有上限，并公开 dropped signal 指标。

