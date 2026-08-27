# 缓存

缓存 API 以类型化 key、value codec 和明确过期策略为核心。缓存是性能层，不能成为唯一事实来源。

```norm
Cache<OrderId, Order> orders = cacheFactory.create(
    name: "orders",
    ttl: duration(seconds: 300, nanoseconds: 0),
    codec: orderCodec
)

Order? cached = orders.get(key: id)
```

## Cache-aside

```norm
Order order = orders.getOrLoad(
    key: id,
    loader: loadOrder
)
```

同一 key 并发 miss 时实现应合并 loader，避免 cache stampede。loader 失败不写缓存；是否短暂缓存“未找到”需要独立 negative TTL。

## 失效

写数据库后删除或更新缓存的顺序必须与一致性目标匹配。跨进程 invalidation 可能延迟，业务不能假设强一致。key 应包含 schema 或语义版本，部署新格式时避免误读旧值。

容量、逐出、命中率、loader 延迟和错误必须可观测。缓存值按值返回，不能让调用者修改进程内共享缓存对象。
