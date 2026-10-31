---
title: Tooling
description: Norm CLI、语言服务与编辑器集成
---

# Tooling

Norm 的 formatter、诊断和编辑器能力读取与编译器相同的语义快照。类型检查、名称解析和项目边界只实现一次。

## 当前能力

- 格式化与编译器诊断；
- 作用域和期望类型驱动的补全；
- Signature Help 与 Hover；
- 跳转定义与查找引用；
- Prepare Rename 与语义 Rename；
- 跨文件、跨 package 和标准库源码导航；
- 未保存文档参与项目分析。

结构化 API 文档复用同一语义入口，见 [API 文档导出](/zh/tooling/api-documentation)。自包含应用的输出约定见[应用构建](/zh/tooling/application-build)。

## 从示例开始

使用 [Norm 0.25](/zh/versions/0.25)，在空目录中运行 `norm hello`，生成五个独立的 `.norm` 程序以及英文、中文说明。先运行 `norm hell.norm`，再按生成的[示例说明](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/resources/hello/README.zh-CN.md)继续体验。

`norm main.norm` 默认不显示启动准备日志。使用 `norm main.norm --debug` 或 `norm run --debug main.norm`，将启动阶段、累计耗时和实际 Maven 下载写入 stderr；该选项同时启用应用及依赖的调试日志，级别优先级与路由见[应用日志](/zh/stdlib/logging)。错误诊断始终显示。进度入口见 [RunCommand](../../../cli/compiler/src/main/java/dev/w0fv1/norm/cli/controller/RunCommand.java)，本地依赖的联网边界验证见 [JarResolverTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JarResolverTest.java)。

## 保存后更新运行中的代码

`norm run main.norm` 与 `norm main.norm` 在 JVM 源码运行期间监听项目输入。保存代码后在执行边界采用新版本并保留运行状态，程序退出时监听随之结束。准备制品缓存复用同一运行入口。

编译失败时继续执行运行中的版本。无法唯一对应的活动区域保留旧版并报告待更新状态。显式状态转换契约见标准库 [StateMigration 与 MigrationState](../../../norm/stdlib/std/runtime/migration.norm)，连续自修改验证见 [SelfMigratingApplicationTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/cli/controller/SelfMigratingApplicationTest.java)。发布制品直接加载准备好的程序。注解引用绑定入口为 [CoreAnnotationSites](../../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreAnnotationSites.java)、[BindingId](../../../cli/compiler/src/main/java/dev/w0fv1/norm/core/BindingId.java) 和 [AnnotationReferenceLinkageExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/AnnotationReferenceLinkageExecutionTest.java)。

公开的更新与批量生命周期见 [ApplicationSession](../../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationSession.java) 和 [ExecutionUpdateResult](../../../cli/compiler/src/main/java/dev/w0fv1/norm/execution/ExecutionUpdateResult.java)，状态兼容规则以 [CoreRuntimeSchema](../../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreRuntimeSchema.java) 为准。验证入口为 [LiveExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveExecutionTest.java)、[LiveStructureExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveStructureExecutionTest.java)、[LiveMigrationBoundaryTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveMigrationBoundaryTest.java)、[LiveRuntimeTypesExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveRuntimeTypesExecutionTest.java) 和 [SelfUpdatingApplicationTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/cli/controller/SelfUpdatingApplicationTest.java)。

运行时身份与 Java 依赖组件入口为 [CoreRuntimeIdentity](../../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreRuntimeIdentity.java)、[JavaRealmPool](../../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaRealmPool.java) 和 [ApplicationRuntimeRevisionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/application/ApplicationRuntimeRevisionTest.java)。事务验证入口为 [LiveContainerMigrationExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveContainerMigrationExecutionTest.java) 和 [LiveConstructionExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveConstructionExecutionTest.java)。事务范围仅为 Norm 状态，外部 I/O 不回滚。

保留对象图的转换实现见 [RuntimeStateTransaction](../../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/RuntimeStateTransaction.java) 与 [RuntimeObjectState](../../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/RuntimeObjectState.java)。可执行契约覆盖[枚举对象图](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveEnumGraphMigrationTest.java)、[泛型替换](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveGenericGraphMigrationTest.java)、[接口变化](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveInterfaceGraphMigrationTest.java)、[反射快照](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveClassSnapshotTest.java)、[调用边界](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveCallableArgumentMigrationTest.java)和[活动迭代器](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveEnumIteratorMigrationTest.java)。

## VS Code

AI Agent 的规范、查询和验证导航见 [Agent 开发入口](/zh/tooling/agent)。

正式 VSIX 包含受支持平台的同版本自包含 CLI。安装、运行、项目识别和 CLI 选择见 [VS Code 开发体验](/zh/guide/vscode)。发布资产与平台矩阵见[发布流程](/zh/design/release-process)。

## 共同语义入口

```text
Source files
  → Project source set
  → Semantic model
  → diagnostics / completion / signatures
  → hover / navigation / references / rename
  → formatter / compiler
```

工具能力的当前交付边界见 [Status](/zh/status)。调试器和在线执行环境尚未进入发布版。
`norm run` 默认运行当前目录，优先选择 `application.norm`，不存在时选择 `main.norm`。可用 `norm run -- 参数...` 在省略路径时传入应用参数。
