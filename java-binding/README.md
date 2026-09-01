# Java Binding

本目录保存 Java artifact、普通 Module 适配声明、Norm 验证程序、API 覆盖报告和发布 NAR。架构目标、阶段门禁与常用 API 适配标准见 [Java Library Adapter](../docs/design/java-library-adapters.md)。

每个直接子目录是独立适配工程和源码根；其中的 `module.norm` 按 Module 名称放置在对应命名空间路径。JAR 与 NAR 使用 Git LFS，所有可编辑依赖和发布声明只存在于 `module.norm`。

当前真实制品：

- `commons-lang`：Apache Commons Lang 3.20.0，发布为 `commons:lang:1`；
- `commons-io`：Apache Commons IO 2.22.0，发布为 `commons:io:1`；
- `jsoup`：jsoup 1.23.2，发布为 `jsoup:jsoup:1`；
- `joda-time`：Joda-Time 2.14.3，发布为 `joda:time:1`；
- `fastutil`：fastutil 8.5.19，发布为 `fastutil:collections:1`；
- `org-json`：org.json 20260814，发布为 `org:json:1`；
- `caffeine`：Caffeine 3.2.4，发布为 `caffeine:cache:1`；
- `guava`：Guava 33.7.1-jre，发布为 `guava:core:1`；
- `okhttp`：OkHttp JVM 5.5.0，发布为 `okhttp:client:1`；
- `eclipse-collections`：Eclipse Collections 13.0.0，发布为 `eclipse:collections:1`。
