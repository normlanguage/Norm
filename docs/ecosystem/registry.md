# Norm Package Registry

仓库只发布 Module artifact。Java Binding 和平台实现是 artifact 内可选的实现元数据，不构成模块种类，也不改变消费者的依赖方式。

Module 的仓库坐标见[包管理器](/ecosystem/package-manager)，Java 依赖与 Maven/Gradle 仓库互操作边界见 [Java Library Adapter](/design/java-library-adapters)。[`normlanguage/registry`](https://github.com/normlanguage/registry) 是 `github` 仓库身份映射的唯一真相源；一个条目只包含 Module 名、GitHub owner 与 repository。源码、版本标签和不可变 NAR Release 归属于各 Module 仓库，Norm 编译器仓库不保存第三方或官方 Java Binding 包。

