# Norm Package Manager

Module 是唯一依赖和发布单位，`module.norm` 是声明与配置的唯一写入口。仓库坐标、发布元数据和外部实现不产生新的包种类。

本地模块图与可见性见[模块系统](/spec/module-system)。Java 生态接入、内容身份、派生 Maven 元数据和纯 Norm 迁移边界见 [Java Library Adapter](/design/java-library-adapters)。

发布 Module 的名称必须是 `<namespace>.<artifact>`，仓库坐标固定派生为 `<namespace>:<artifact>:<version>`。例如 `commons.lang@1` 发布为 `commons:lang:1`，制品名为 `lang-1.nar`。NAR 是 Norm Archive；`norm resolve` 只写 `module.norm` 中的内容摘要，`norm package` 输出 Maven 仓库布局、NAR 和 POM，Gradle 使用同一 Maven 元数据。

NAR 是统一的 Module 制品。纯 Norm Module 与 Java Binding Module 使用相同的坐标、依赖模型和归档格式；Binding 只是可选实现，移除 Binding 后不改变包种类或消费者的调用方式。

