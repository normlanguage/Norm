# Java Binding

本目录保存 Java artifact、普通 Module 适配声明、Norm 验证程序、API 覆盖报告和发布 NAR。架构目标、阶段门禁与常用 API 适配标准见 [Java Library Adapter](../docs/design/java-library-adapters.md)。

每个直接子目录是独立适配工程和源码根；其中的 `module.norm` 按 Module 名称放置在对应命名空间路径。JAR 与 NAR 使用 Git LFS，所有可编辑依赖和发布声明只存在于 `module.norm`。POM 由 `norm package` 写入目标仓库，不存放在本目录。

完整 API census、覆盖状态和未支持原因封装在每个 NAR 的 `binding/java-api.json`，公开适配面封装在 `module.json`。NAR 只携带生成的公开适配源码；目录中的 `Main.norm`、测试和其他验证程序保留为源码工程资产。真实行为与独立消费门禁集中在编译器的 Java Binding 集成测试中，适配目录的 README 指向对应示例和测试入口。

当前真实制品：

- `orm-api`：Jakarta Persistence 3.2 的 Norm ORM 公共面，发布为 `orm@1`；
- `orm-hibernate`：Hibernate ORM 7.4.7.Final Provider，发布为 `orm.hibernate@1`；
- `orm-micronaut`：Micronaut SQL 7.1.2 的 ORM 生命周期集成，发布为 `orm.micronaut@1`；
- `commons-lang`：Apache Commons Lang 3.20.0，发布为 `commons:lang:1`；
- `commons-io`：Apache Commons IO 2.22.0，发布为 `commons:io:1`；
- `jsoup`：jsoup 1.23.2，发布为 `jsoup:jsoup:1`；
- `joda-time`：Joda-Time 2.14.3，发布为 `joda:time:1`；
- `fastutil`：fastutil 8.5.19，发布为 `fastutil:collections:1`；
- `org-json`：org.json 20260814，发布为 `org:json:1`；
- `caffeine`：Caffeine 3.2.4，发布为 `caffeine:cache:1`；
- `guava`：Guava 33.7.1-jre，发布为 `guava:core:1`；
- `okhttp`：OkHttp JVM 5.5.0，发布为 `okhttp:client:1`；
- `eclipse-collections`：Eclipse Collections 13.0.0，发布为 `eclipse:collections:1`；
- `micronaut-core`：Micronaut Core 5.1.13，发布为 `micronaut:core:1`；
- `jakarta-annotation`：Jakarta Annotation 2.1.1，发布为 `jakarta:annotation:1`；
- `jakarta-inject`：Jakarta Inject 2.0.1，发布为 `jakarta:inject:1`；
- `micronaut-http`：Micronaut HTTP 5.1.13，发布为 `micronaut:http:1`；
- `micronaut-http-client-core`：Micronaut HTTP Client Core 5.1.13，发布为 `micronaut.http:client:1`；
- `micronaut-http-client`：Micronaut HTTP Client Netty 5.1.13，发布为 `micronaut.http.client:netty:1`；
- `micronaut-management`：Micronaut Management 5.1.13，发布为 `micronaut:management:1`；
- `micronaut-aop`：Micronaut AOP 5.1.13，发布为 `micronaut:aop:1`；
- `micronaut-inject`：Micronaut Inject 5.1.13，发布为 `micronaut:inject:1`；
- `micronaut-inject-java`：Micronaut Inject Java 5.1.13 官方 JSR 269 处理器，发布为 `micronaut.inject:processor:1`；
- `micronaut-runtime`：Micronaut Context 5.1.13，发布为 `micronaut:runtime:1`；
- `micronaut-http-server-netty`：Micronaut HTTP Server Netty 5.1.13，发布为 `micronaut.server:netty:1`；
- `micronaut-json`：Micronaut JSON Core 5.1.13，发布为 `micronaut:json:1`；
- `micronaut-serde-api`：Micronaut Serialization API 3.1.1，发布为 `micronaut.serde:api:1`；
- `micronaut-serde-jackson`：Micronaut Serialization Jackson 3.1.1，发布为 `micronaut.serde:jackson:1`；
- `micronaut-serde-processor`：Micronaut Serialization 3.1.1 官方处理器，发布为 `micronaut.serde:processor:1`；
- `micronaut-jackson`：Micronaut Jackson Databind 5.1.13，发布为 `micronaut:jackson:1`；
- `jakarta-validation`：Jakarta Validation API 3.1.1 常用约束 Annotation，发布为 `jakarta:validation:1`；
- `micronaut-validation`：Micronaut Validation 5.1.0，发布为 `micronaut:validation:1`；
- `micronaut-validation-processor`：Micronaut Validation 5.1.0 官方编译期访问器，发布为 `micronaut.validation:processor:1`；
- `micronaut-data-model`：Micronaut Data Model 5.1.3，发布为 `micronaut.data:model:1`；
- `micronaut-data-jdbc`：Micronaut Data JDBC 5.1.3，发布为 `micronaut.data:jdbc:1`；
- `micronaut-data-processor`：Micronaut Data 5.1.3 官方编译期处理器，发布为 `micronaut.data:processor:1`；
- `micronaut-data-tx`：Micronaut Data Transaction 5.1.3，发布为 `micronaut.data:tx:1`；
- `micronaut-jdbc-hikari`：Micronaut JDBC Hikari 7.1.2，发布为 `micronaut.jdbc:hikari:1`；
- `h2-database`：H2 Database 2.4.240，发布为 `h2:database:1`；
- `micronaut-security`：Micronaut Security 5.3.2，发布为 `micronaut:security:1`；
- `micronaut-websocket`：Micronaut WebSocket 5.1.13，发布为 `micronaut:websocket:1`；
- `micronaut-test-core`：Micronaut Test Core 5.1.0，发布为 `micronaut.test:core:1`；
- `micronaut-test-junit5`：Micronaut Test JUnit 5 5.1.0，发布为 `micronaut.test:junit5:1`；
- `junit-jupiter`：JUnit Jupiter API 6.1.2，发布为 `junit:jupiter:1`；
- `reactor-core`：Reactor Core 3.8.7，发布为 `reactor:core:1`。
