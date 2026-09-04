# Norm Package Manager

Module 是唯一依赖和发布单位，`Module module()` 是模块身份与依赖的唯一写入口。多文件模块通常把它放在 `module.norm`；单文件应用可以把它和业务声明、`Application application()` 放在同一个 `.norm` 文件中，并直接执行 `norm <file.norm>`。仓库坐标、发布元数据和外部实现不产生新的包种类。

本地模块图与可见性见[模块系统](/spec/module-system)。Java 生态接入、内容身份、派生 Maven 元数据和纯 Norm 迁移边界见 [Java Library Adapter](/design/java-library-adapters)。

带命名空间的 Module `<namespace>.<artifact>` 固定发布为 `<namespace>:<artifact>:<version>`。顶层 Module 使用自身名称作为仓库 group 与 artifact，例如 `orm@1` 发布为 `orm:orm:1`，制品名仍为 `orm-1.nar`。`commons.lang@1` 发布为 `commons:lang:1`，制品名为 `lang-1.nar`。NAR 是 Norm Archive；`norm resolve` 只写 `module.norm` 中的内容摘要，`norm package` 输出 Maven 仓库布局、NAR 和 POM，Gradle 使用同一 Maven 元数据。

NAR 是统一的 Module 制品。纯 Norm Module 与 Java Binding Module 使用相同的坐标、依赖模型和归档格式；Binding 只是可选实现，移除 Binding 后不改变包种类或消费者的调用方式。

外部依赖显式声明 Norm 包来源：

```norm
dependency(repository: "github", name: "micronaut.web")
```

`version` 可以省略；此时仓库选择最新的稳定整数版本，并立即把它解析为精确坐标。发布 NAR 时只写入解析后的精确依赖版本。显式版本继续直接选择 `v<version>`。

`github` 先读取 [`normlanguage/registry`](https://github.com/normlanguage/registry) 的模块名映射，再从模块自己的 GitHub Release `v<version>` 下载 `<artifact>-<version>.nar` 与 SHA-256 sidecar。Release 开启不可变发布；注册表只决定模块由哪个仓库拥有，不保存版本清单或制品。NAR 中的 Java Binding 再由编译器从 Maven Central 解析 JAR、POM、BOM 和传递运行依赖。Norm 包来源与 Java artifact 仓库是独立概念；用户不编写 Maven 或 Gradle 配置。

每个 Module 使用独立源码仓库，`module.norm` 仍是模块身份、依赖与可选 Java 根制品的唯一声明源。第三方先通过注册表 Pull Request 认领模块名，随后可在自己的仓库独立发布新版本；未来增加 `norm` 仓库不会改变 `github` 的解析语义。

