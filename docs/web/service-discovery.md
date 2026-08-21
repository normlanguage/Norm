# 服务发现

服务发现把逻辑服务名解析为一组可连接 endpoint。DNS、静态配置和注册中心通过同一接口适配，但保留各自一致性限制。

```norm
interface ServiceResolver {
    Result<List<ServiceEndpoint>, DiscoveryError> resolve(ServiceName name)
}
```

endpoint 包含地址、端口、协议、可选区域与权重。解析结果为空与解析失败是不同状态。

## 缓存和更新

resolver 根据 TTL 缓存结果，并可以提供 watch stream。更新必须以完整快照或带版本 diff 交付，客户端不能混合两个版本的部分列表。

## 选择

负载均衡器接收健康 endpoint 列表和请求上下文，选择策略可以是 round-robin、least-loaded 或 locality-aware。重试应换 endpoint，但仍受总 deadline 和幂等限制。

## 故障

注册中心不可用时是否使用未过期缓存、陈旧缓存或直接失败必须配置。服务发现本身不执行业务健康检查；它组合来自注册源和连接结果的信号。

解析与选择决策应通过低基数指标和 trace 属性可见。

