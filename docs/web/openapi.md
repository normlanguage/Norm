# OpenAPI

OpenAPI 文档由显式路由描述与 Codec schema 生成，不从运行中 controller 的内部实现猜测契约。

```norm
router.get(
    path = "/orders/{id}",
    handler = orders.get,
    operation = Operation<OrderResponse>(
        id = "getOrder",
        summary = "Get an order",
        response = orderResponseCodec.schema()
    )
)
```

## 生成检查

构建阶段验证 operation id 唯一、所有 path 参数有声明、响应状态有 schema、nullable 映射一致。无法生成 schema 的类型导致构建失败，而不是输出空 object。

## 稳定输出

生成结果字段和 component 按确定顺序输出，便于 code review。CI 保存基线并执行 breaking-change diff，包括删除 endpoint、收紧输入、改变响应类型和 enum variant。

## 运行时

是否暴露 `/openapi.json` 与交互 UI 由部署配置决定。生产环境关闭 UI 不影响构建产物。安全 scheme 只描述协议，不能把真实 token、client secret 或内部 endpoint 写进文档。

