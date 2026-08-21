# 路由设计

路由表通过普通代码构造，启动时完成冲突检查。框架不扫描 annotation，也不根据函数名推断 URL。

```norm
Router router = Router()
router.get(path: "/users/{id}", handler: users.get)
router.post(path: "/users", handler: users.create)
```

## 匹配

请求先按规范化 path 分段，再匹配静态段、类型化参数段和 wildcard。优先级固定为静态段高于参数段、高于 wildcard，不依赖注册顺序解决含糊路由。

```norm
router.get(path: "/files/{path...}", handler: files.get)
```

重复的 method + path、无法区分的参数路由和非末尾 wildcard 在启动时失败。

## 参数

路径参数首先是 String，handler 使用显式 parser 转成领域类型。解析失败产生 400，而资源不存在产生 404。query 的重复键、空值和缺失必须由类型化访问器区分。

## 组合

子路由可以挂载 prefix 和 middleware，但组合结果仍是一张可枚举路由表，可用于 OpenAPI 生成、冲突检测和测试。

