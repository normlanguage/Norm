# Norm Package Registry

仓库只发布 Module artifact。Java Binding 和平台实现是 artifact 内可选的实现元数据，不构成模块种类，也不改变消费者的依赖方式。

Module 的仓库坐标见[包管理器](/ecosystem/package-manager)，Java 依赖与 Maven/Gradle 仓库互操作边界见 [Java Library Adapter](/design/java-library-adapters)。`github` 仓库以 [`normlanguage/Norm`](https://github.com/normlanguage/Norm) monorepo 和发布流水线为真相源，编译器、标准库、组合 Module 与 `java-binding/` 由同一仓库管理，通过 GitHub Pages 发布不可变 NAR 与摘要；注册表只索引编译后的模块描述和派生产物，不执行下载包中的 `module()`。

