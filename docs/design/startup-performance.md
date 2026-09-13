---
title: 应用启动性能
description: 源码运行的性能目标、制品复用边界与验收
---

# 应用启动性能

目标是让输入未变化的 `norm run` 直接执行准备好的应用制品，绕过编译器初始化、语义模型恢复和 Java 绑定生成，并以同一框架的 Java 程序作为启动对照。JVM 构建产物使用相同执行制品。用户入口保持 `norm run` 与 `norm build`，缓存位于用户目录，不要求示例脚本或后台守护进程。

## 测量

Jetty、独立 Vaadin Todo、Spring Web + Vaadin 分别测量首次运行、重复运行和源码修改后的运行。对照相同 JDK、依赖版本、框架配置的 Java 程序与 Norm JVM 制品。区分进程创建、首次进度、进入应用和 HTTP 就绪；浏览器交互独立验收。每组至少三次，记录原始结果和中位数；依赖下载与本地缓存场景分开。

测量入口为 [measure-startup.mjs](../../cli/compiler/scripts/measure-startup.mjs)，输出包含命令、工作目录、原始日志、首次输出和 HTTP 响应时间。使用 `--command`、`--cwd`、`--output` 指定测量对象与报告，`--` 后传入应用参数。被测程序应打印本机 HTTP 地址；测量器负责结束自己创建的进程树。

## 落地顺序

1. 统一 Core 程序与 Java 调用描述的可执行数据，复用 Native 构建已使用的序列化机制。
2. JVM 构建捕获程序、生成类、资源与依赖；运行时直接加载，不交付源码编译入口。
3. 源码运行记录输入内容与发现边界，原子发布准备好的应用；输入未变时使用同一运行入口。
4. 动态模块使用已编译的模块程序重新求值，结果变化时重新装配；不默认缓存外部注解处理器结果。
5. 验证独立进程、源码删除后的构建产物执行、输入失效和真实浏览器操作，更新本机 CLI，并与 Java 对照复测冷暖启动。

## 正确性边界

缓存身份必须覆盖编译器及 ABI、源码内容和位置、模块解析输入、NAR/JAR 内容、资源、编译选项和处理器输入。新增与删除文件同样影响身份；不能仅比较修改时间，不能复用旧诊断位置或旧资源。

动态模块声明与注解处理器读取的外部输入不能默认视为纯函数。只有可表达完整输入边界的产物才能复用；无法追踪的输入必须保持重新求值。并发写入原子发布，读取校验内容，失败不发布成功产物，运行状态和类加载器不进入持久缓存。

验收包含独立进程的重复命中、源码/依赖/资源变更、同时间戳内容变更、新增与删除文件、损坏缓存、并发运行和诊断位置。仅运行相关测试与真实示例，不以跳过校验换取计时结果。

## 实现入口

- [PreparedApplication](../../cli/compiler/src/main/java/dev/w0fv1/norm/runtime/PreparedApplication.java)：JVM 制品与源码快速路径共用的执行入口。
- [ApplicationProgramPlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationProgramPlan.java)：JVM 与 Native 共用的可达性和调用保留计划。
- [PreparedApplicationCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/PreparedApplicationCache.java)：执行制品复用和模块重新求值。
- [ProjectInputSnapshot](../../cli/compiler/src/main/java/dev/w0fv1/norm/project/ProjectInputSnapshot.java)：源码、发现边界、资源和依赖的内容验证。
- [PreparedApplicationCacheTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/application/PreparedApplicationCacheTest.java) 与 [PreparedApplicationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/application/PreparedApplicationTest.java)：快速路径失效与脱离原始源码的执行验收。

- [编译器架构](/spec/compiler-design)：现有增量模型与内容身份。
- [ApplicationRunner](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationRunner.java)：应用装配与执行。
- [ApplicationCompiler](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationCompiler.java)：编译及 Java 生成产物。
- [CompilerSession](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilerSession.java)：编译会话与定义存储。
- [CompilationResultCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilationResultCache.java)：纯编译结果的输入身份与跨会话复用。
- [FileArtifactCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/FileArtifactCache.java)：有界存储、内容校验和并发发布。
- [PersistentCompilationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PersistentCompilationTest.java)：跨会话复用、源码变化与损坏内容验证。
- [JarResolver](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarResolver.java)：Java 依赖解析。
- [JarGraphCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarGraphCache.java) 与 [JarApiCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarApiCache.java)：固定依赖图和 Java API 派生产物复用。
- [CompilerArtifactIdentity](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/CompilerArtifactIdentity.java)：各类派生缓存共用的编译器身份。
- [PersistentModuleEvaluationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/PersistentModuleEvaluationTest.java)：缓存命中时仍执行模块函数。
- [JavaCompilationCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaCompilationCache.java)：生成 Java 类的源码、类路径与 JDK 工具链身份；接入点为 [JavaAnnotationProcessorPipeline](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaAnnotationProcessorPipeline.java)。
- [JavaAnnotationBindingIntegrationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaAnnotationBindingIntegrationTest.java)：缓存命中后的 Java 回调与注解处理器执行边界。
- [PersistentApplicationResourcesTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/PersistentApplicationResourcesTest.java)：编译缓存命中后的资源修改与删除。
- [FileArtifactCacheTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/store/FileArtifactCacheTest.java)：独立进程并发与损坏缓存。
