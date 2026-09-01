# Micronaut BBS

`app/sample/bbs` 是纯 Norm Micronaut 应用，`module.norm` 声明全部依赖。应用使用官方 Micronaut Processor 生成 Controller、DI、AOP、Serde、Validation、Security、Filter、Data Repository、事务和全局 Error Handler 元数据，通过 Netty、Micronaut HTTP Client、Hikari、H2 与 OkHttp 进行真实端到端验收。

从已经发布适配 NAR 的仓库运行：

```text
norm run docs/examples/micronaut-bbs/app/sample/bbs/Main.norm
```

浏览器 UI 位于 `http://127.0.0.1:8080/`，HTTP API 位于 `/bbs`。服务持续运行，数据保存在项目 `.tmp/micronaut-bbs`，按 `Ctrl+C` 停止。

当前应用覆盖响应式 HTML UI、注册与登录会话、板块、主题、回复、分页、SSE 通知、Controller、DI、Around Advice、Serde JSON、校验、持久化、事务、Basic Authentication、数据库会话 Filter、官方 Health Endpoint 和领域异常 JSON 响应。`BbsTest.norm` 使用真实 Micronaut Test/JUnit Platform 验证 Bean 注入与凭证策略；端到端门禁还覆盖静态资源、Micronaut HTTP Client、12 路并发写入、BBS NAR 发布以及独立项目从全新 NAR 仓库消费，见 `MicronautBindingIntegrationTest`。
