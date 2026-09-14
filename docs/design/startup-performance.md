---
title: 应用启动性能
description: 源码运行的性能目标、制品复用边界与验收
---

# 应用启动性能

目标是减少包消费、应用准备和 JVM 启动的实际工作。稳定的 Java 适配信息在包发布时准备，应用专属信息在构建时链接，进程状态在运行时创建。源码运行与 JVM 构建产物共享执行制品，保持单一 Core/Truffle 执行链。用户入口保持 `norm package`、`norm run` 与 `norm build`，不要求示例脚本或后台守护进程。

## 测量

Jetty、独立 Vaadin Todo、Spring Web + Vaadin 分别测量首次运行、重复运行和源码修改后的运行。对照相同 JDK、依赖版本、框架配置的 Java 程序与 Norm JVM 制品。区分进程创建、首次进度、进入应用和 HTTP 就绪；浏览器交互独立验收。每组至少三次，记录原始结果和中位数；依赖下载与本地缓存场景分开。

测量入口为 [measure-startup.mjs](../../cli/compiler/scripts/measure-startup.mjs)，输出包含命令、工作目录、原始日志、首次输出和 HTTP 响应时间。使用 `--command`、`--cwd`、`--output` 指定测量对象与报告，`--` 后传入应用参数。被测程序应打印本机 HTTP 地址；测量器负责结束自己创建的进程树。

## 落地顺序

1. 冻结运行库、CLI、包与测试输入，保留同 JDK 对照。建立修改函数体、修改签名、资源变化、独立进程和迁移源码位置的工作量测试。
2. 建立无需应用入口的模块编译契约。发布前完成业务语义检查，模块产物保留公开声明、Core、依赖身份与源码映射；错误模块不能发布成功。
3. 让源码与归档消费同一声明契约，将模块依赖作为编译边界。标准库与正式包复用同一机制；泛型、默认参数、注解和 Java 绑定所需信息来自同一编译结果。
4. 贯通跨进程增量历史与下游构建。变化声明及必要依赖重新分析，未变化的模块与 Core 定义直接复用；源码位置与实现内容分别失效，诊断使用当前映射。 区分声明 ABI 变化与实现变化；函数体修改引起的执行链接更新，不应自动要求重新绑定、转换所有调用方。
5. 统一构建事务内的制品捕获、依赖选择与物化。稳定依赖、生成类和资源按内容引用；应用缓存先检查轻量输入索引，再加载需要的产物。按内容键协调生产，发布与回收保持短临界区。
6. 以发布包 Core 导入、调用方链接复用作为优化收口边界。完成三个真实示例、浏览器交互、Java 回调、并发缓存及 CLI 安装验收，审查后分步提交。初始化和类加载等其他热点不扩展为新增优化任务。

每项优化同时验收工作量与最终延迟：包消费是否重新扫描和生成适配、暖启动是否复制依赖、执行准备遍历和类型加载数量是否减少。降低某一内部阶段耗时而导致总启动或应用语义退化，不视为完成。

修改后的验收以独立进程为边界，记录解析文档、分析声明、实际 Core 生成与依赖复制数量。Core 身份相同与历史报告中的定义总数不代表省掉了本次计算。发布包无需重新分析依赖源码，无关资源和 Java 桥接产物应继续复用。

## 正确性边界

缓存身份必须覆盖编译器及 ABI、源码内容和位置、模块解析输入、NAR/JAR 内容、资源、编译选项和处理器输入。新增与删除文件同样影响身份；不能仅比较修改时间，不能复用旧诊断位置或旧资源。

动态模块声明与注解处理器读取的外部输入不能默认视为纯函数。只有可表达完整输入边界的产物才能复用；无法追踪的输入必须保持重新求值。并发写入原子发布，读取校验内容，失败不发布成功产物，运行状态和类加载器不进入持久缓存。

验收包含独立进程的重复命中、源码/依赖/资源变更、同时间戳内容变更、新增与删除文件、损坏缓存、并发运行和诊断位置。仅运行相关测试与真实示例，不以跳过校验换取计时结果。

## 实现入口

- [PreparedApplication](../../cli/compiler/src/main/java/dev/w0fv1/norm/runtime/PreparedApplication.java)：JVM 制品与源码快速路径共用的执行入口。
- [ApplicationProgramPlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationProgramPlan.java)：JVM 与 Native 共用的可达性和调用保留计划。
- [PreparedApplicationCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/PreparedApplicationCache.java)：输入索引先于执行制品读取，模块重新求值与缓存主体独立失效。
- [DirectoryArtifactCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/DirectoryArtifactCache.java) 与 [DirectoryArtifactCacheTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/store/DirectoryArtifactCacheTest.java)：应用文件的原子发布、进程持有、容量回收和损坏修复。
- [ArtifactFileSet](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/ArtifactFileSet.java) 与 [ResolvedJarClasspathTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/ResolvedJarClasspathTest.java)：依赖文件集的内容身份、类路径持有和变化后的复用；无 Java 依赖时不创建缓存目录。
- [PublishedJarBinding](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/PublishedJarBinding.java) 与 [ModulePackagerTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/ModulePackagerTest.java)：发布绑定的内容、ABI、依赖图及源码一致性。
- [CompiledModule](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompiledModule.java) 与 [PublishedCoreImportTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PublishedCoreImportTest.java)：模块 Core 导入、依赖实现重链接及跨进程缓存的内容身份。
- [AnalysisJournal](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/AnalysisJournal.java) 与 [AnalysisTransactionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/AnalysisTransactionTest.java)：语义试探的增量撤销与嵌套事务。
- [FileSnapshot](../../cli/compiler/src/main/java/dev/w0fv1/norm/value/FileSnapshot.java) 与 [FileSnapshotTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/value/FileSnapshotTest.java)：复制过程中验证内容，失败不替换目标文件。
- [ProjectInputSnapshot](../../cli/compiler/src/main/java/dev/w0fv1/norm/project/ProjectInputSnapshot.java)：源码、发现边界、资源和依赖的内容验证。
- [PreparedApplicationCacheTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/application/PreparedApplicationCacheTest.java) 与 [PreparedApplicationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/application/PreparedApplicationTest.java)：快速路径失效与脱离原始源码的执行验收。

- [编译器架构](/spec/compiler-design)：现有增量模型与内容身份。
- [ApplicationRunner](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationRunner.java)：应用装配与执行。
- [ApplicationCompiler](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationCompiler.java)：编译及 Java 生成产物。
- [CompilerSession](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilerSession.java)：编译会话与声明历史。
- [DeclarationIdentity](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/DeclarationIdentity.java) 与 [PortableDeclarationIdentityTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PortableDeclarationIdentityTest.java)：发布者与消费者目录之间的声明身份、私有类型及泛型参数一致性。
- [CoreBindingShape](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreBindingShape.java) 与 [DefaultArgumentContractTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/DefaultArgumentContractTest.java)：参数声明、默认实现链接及持久复用；声明签名与默认实现路由分别参与身份计算。
- [CompilationResultCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilationResultCache.java)：纯编译结果的输入身份与跨会话复用。
- [FileArtifactCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/FileArtifactCache.java)：有界存储、内容校验和并发发布。
- [PersistentCompilationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PersistentCompilationTest.java)：跨会话复用、源码变化与损坏内容验证。
- [CoreCompilationInput](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreCompilationInput.java) 与 [CoreCompilationInputTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreCompilationInputTest.java)：源码与已编译定义的混合输入、递归组及传递依赖链接。
- [CoreReusePlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CoreReusePlan.java) 与 [CoreDependencyTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreDependencyTest.java)：实际声明依赖、内容相同的不同调用目标和当前源码映射。
- [CoreCanonicalizerTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreCanonicalizerTest.java)：深依赖图、单成员分组与递归对称性的规范身份约束。
- [JarResolver](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarResolver.java)：Java 依赖解析。
- [JarGraphCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarGraphCache.java) 与 [JarApiCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarApiCache.java)：固定依赖图和 Java API 派生产物复用。
- [CompilerArtifactIdentity](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/CompilerArtifactIdentity.java)：各类派生缓存共用的编译器身份。
- [PersistentModuleEvaluationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/PersistentModuleEvaluationTest.java)：缓存命中时仍执行模块函数。
- [JavaCompilationCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaCompilationCache.java)：生成 Java 类的源码、类路径与 JDK 工具链身份；接入点为 [JavaAnnotationProcessorPipeline](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaAnnotationProcessorPipeline.java)。
- [JavaAnnotationBindingIntegrationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaAnnotationBindingIntegrationTest.java)：缓存命中后的 Java 回调与注解处理器执行边界。
- [PersistentApplicationResourcesTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/PersistentApplicationResourcesTest.java)：编译缓存命中后的资源修改与删除。
- [FileArtifactCacheTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/store/FileArtifactCacheTest.java)：独立进程并发与损坏缓存。
