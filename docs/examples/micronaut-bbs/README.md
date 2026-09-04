# Micronaut BBS

`app/sample/bbs` 是纯 Norm Micronaut 应用。`application.norm` 使用 `MicronautConfig` 下的 `micronaut`、`datasources`、`jpa` 与 `endpoints` 类型树声明应用配置，结构直接对应 `application.yml`。`std.configuration` 从这棵树派生 Micronaut 属性，启动器不维护字符串 key。`module.norm` 只声明三个生产依赖。应用按 `Web.norm`、`service`、`repository` 三层组织；每种实体的 Repository 继承 `Repository<E, I>` 获得通用 CRUD，只声明领域查询，Service 持有事务和业务对象创建，Web 层只处理 HTTP。Hibernate Provider 和官方 Micronaut Processor 接入持久化上下文、Controller、构造器 DI、Serde、Validation、Security 和 Filter。

从已经发布适配 NAR 的仓库运行：

```text
norm run docs/examples/micronaut-bbs/app/sample/bbs
```

浏览器 UI 位于 `http://127.0.0.1:8080/`，HTTP API 位于 `/bbs`。服务持续运行，数据保存在项目 `.tmp/micronaut-bbs`，按 `Ctrl+C` 停止。

当前应用覆盖响应式 HTML UI、注册与登录会话、板块、主题、回复、分页、Controller、DI、Serde JSON、校验、持久化、事务、数据库会话 Filter 和官方 Health Endpoint。服务由通用 `application()` 入口自动启动并在取消时关闭；真实 Netty、Hikari 与 H2 端到端门禁见 `MicronautBindingIntegrationTest`。
