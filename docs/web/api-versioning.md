# API 版本管理

API 版本是传输契约的一部分，与 Norm 语言版本和应用发布版本分开。服务必须选择一种主要策略并保持一致。

## 版本位置

推荐在路径中使用 major 版本：

```text
/v1/orders/{id}
/v2/orders/{id}
```

Header 版本适合需要内容协商的内部 API，但必须进入缓存键和 OpenAPI 描述。查询参数不作为默认版本策略。

## 路由注册

```norm
router.get(
    path = "/v1/orders/{id}",
    handler = ordersV1.get
)
```

版本差异通过不同 handler 或显式 adapter 表达，不在一个函数内部散布全局 `currentApiVersion` 判断。

## 兼容性

同一 major 版本内可以新增可选响应字段和新 endpoint；删除字段、改变含义、收紧有效输入或新增必填字段需要新 major。错误 code 也属于公开契约。

## 弃用

弃用说明包含替代版本、停止支持日期、流量观测和迁移示例。服务可以返回 `Deprecation` 与 `Sunset` header，但不能在未通知客户端时静默切换默认版本。

