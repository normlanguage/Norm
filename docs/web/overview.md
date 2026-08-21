# Web 平台概览

Norm Web 是建立在语言和标准库之上的应用平台，不是 Norm 语法的一部分。学习语言不需要先理解 Controller、依赖注入或 HTTP annotation。

> 当前页面描述候选 API，尚无可用于生产的 Web 实现。

## 组件

- HTTP server、Request 与 Response；
- 显式 Router 和 Middleware；
- JSON、验证与 OpenAPI；
- Authentication、Session 与 Security；
- SQL、缓存、队列和后台任务；
- 日志、指标与分布式追踪；
- 部署与云 adapter。

## 显式路由

```norm
HttpResponse getUser(HttpRequest request) {
    UserId id = UserId.parse(text = request.path.string(name = "id"))
    return userResponses.fromResult(result = users.find(id = id))
}

router.get(path = "/users/{id}", handler = getUser)
```

method、path 和 handler 在一处注册。启动过程可以检查冲突并导出路由表，不需要扫描 class 或依赖运行时 annotation 才知道应用入口。

## 依赖与共享

应用在 main 中构造依赖，通过构造器传给 Controller 或 handler。数据库连接池等共享资源必须使用本身具有明确共享契约的库类型，或以 `Ref<T>` 暴露共享；普通 class 赋值仍遵循语言的值语义。

```norm
OrderController orders = OrderController(
    service = OrderService(repository = repository)
)
```

## 错误边界

输入错误和业务失败使用 Result 或 enum；网络中断、驱动损坏等异常由顶层错误边界记录并转换成稳定响应。框架不会用 null 或隐式异常传播混合这些路径。

