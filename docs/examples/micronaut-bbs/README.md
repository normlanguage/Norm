# Micronaut BBS

`app/sample/bbs` 是纯 Norm Micronaut 应用。`application.norm` 使用 `MicronautConfig`、`Server`、`Persistence`、`Security` 等 Norm 类型声明应用配置，`module.norm` 只声明三个生产依赖。应用使用 Norm ORM 定义实体、Repository、字段引用查询与事务，由 Hibernate Provider 和官方 Micronaut Processor 接入持久化上下文、Controller、构造器 DI、Serde、Validation、Security 和 Filter。

从已经发布适配 NAR 的仓库运行：

```text
norm run docs/examples/micronaut-bbs/app/sample/bbs
```

浏览器 UI 位于 `http://127.0.0.1:8080/`，HTTP API 位于 `/bbs`。服务持续运行，数据保存在项目 `.tmp/micronaut-bbs`，按 `Ctrl+C` 停止。

当前应用覆盖响应式 HTML UI、注册与登录会话、板块、主题、回复、分页、Controller、DI、Serde JSON、校验、持久化、事务、数据库会话 Filter 和官方 Health Endpoint。服务由通用 `application()` 入口自动启动并在取消时关闭；真实 Netty、Hikari 与 H2 端到端门禁见 `MicronautBindingIntegrationTest`。
