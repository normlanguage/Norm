# 指标

指标 API 提供 Counter、Gauge 和 Histogram。指标名称、单位和 label schema 在注册时固定，运行中不能随数据动态改变。

```norm
Counter requests = metrics.counter(
    name = "http.server.requests",
    unit = "request",
    labels = ["method", "route", "status_class"]
)

requests.add(
    value = 1,
    labels = ["GET", "/orders/{id}", "2xx"]
)
```

## 类型选择

- Counter 只累加，用于请求数、错误数和处理字节；
- Gauge 表示可上可下的当前值，如队列深度；
- Histogram 记录延迟或大小分布，并由后端计算分位数。

## Label 约束

label 值必须来自有界集合。路由使用模板而不是实际路径，状态码通常聚合为类别。注册器可以设置 cardinality 上限，超过上限的 series 被拒绝并计入诊断指标。

## 进程和运行时

平台默认暴露启动时间、内存、GC、线程或任务数以及观测导出丢弃量。应用指标使用独立命名空间，避免覆盖运行时指标。

采集端点是否公开、是否需要认证以及监听地址必须在部署配置中显式决定。

