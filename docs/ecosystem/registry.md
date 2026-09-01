# Norm Package Registry

仓库只发布 Module artifact。Java Binding 和平台实现是 artifact 内可选的实现元数据，不构成模块种类，也不改变消费者的依赖方式。

Module 的仓库坐标见[包管理器](/ecosystem/package-manager)，Java 依赖与 Maven/Gradle 仓库互操作边界见 [Java Library Adapter](/design/java-library-adapters)。注册表只索引编译后的模块描述和派生产物，不重新执行发布者的 `module.norm`。

