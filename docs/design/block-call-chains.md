---
title: 块调用链设计与落地方案
description: 普通成员调用的省点号连接、迁移边界与验证入口
---

# 块调用链设计与落地方案

| 项目 | 内容 |
| --- | --- |
| 设计状态 | implemented |
| 语言版本 | Norm 0.23.0 |
| 语言规则 | [函数高级规则：块调用链](../spec/grammar/functions-advanced.md#块调用链) |
| 变更性质 | 调用语法扩展；不增加 Task 运行机制或标准库双回调 API |

## 决策

采用“尾随闭包调用之后继续普通成员调用”的模型，唯一语义为依次调用，每段接收前段的返回值。不采用多回调实参、通用中缀表达式、管道操作符、Task 专用 AST、关键字白名单、自动安全调用或任务展开。句法条件不依赖类型查找，不以大小写或 API 名称猜测独立语句。

规范的唯一入口是[函数高级规则](../spec/grammar/functions-advanced.md#块调用链)；Task 的声明与运行契约分别见 [tasks.norm](../../norm/stdlib/std/concurrent/tasks.norm) 和[并发 API](../stdlib/concurrency.md)。

## 实现边界

| 位置 | 职责与约束 |
| --- | --- |
| [BlockCallChainSyntax](../../cli/compiler/src/main/java/dev/w0fv1/norm/syntax/BlockCallChainSyntax.java) | 中立 token/同行判定，由解析、连接指纹和补全复用；不引用 frontend、semantic 或 UI。 |
| [Parser](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/Parser.java) | 在同一 postfix 循环验证真实前件及控制结构深度，失败不消费 token，成功生成普通 Member 后复用尾随 Lambda 逻辑。 |
| [Syntax](../../cli/compiler/src/main/java/dev/w0fv1/norm/syntax/Syntax.java) | 复用 Member、Call 和 CallArgument；每次调用仍最多一个尾随 Lambda，不新增链节点、Bound IR 或 Core opcode。 |
| [IncrementalAnalysisPlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/IncrementalAnalysisPlan.java) | token 指纹记录保守的连接候选标记，而非绝对行号或全部空白；连接资格改变时失效声明及依赖者。与 Parser 属于同一正确性交付边界。 |
| [CompilerSession](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilerSession.java) | 捕获源码缓存继续做精确文本比较。热 LSP 必须重启，磁盘 JAR 更新不代表进程已更新。 |
| [CoreIdentityVersion](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreIdentityVersion.java) | 语言语义身份隔离旧缓存；不因语法糖改变 Core schema 或 stdlib ABI。该身份不是按来源切换解析器的语法开关。 |
| [SourceFormatter](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/SourceFormatter.java) | 在完整 Call 上选择规范拼写，保留语句边界和不可折行连接头；不建立源码拼写偏好。 |
| [MemberAccessSite](../../cli/compiler/src/main/java/dev/w0fv1/norm/language/MemberAccessSite.java) / [CompletionContextResolver](../../cli/compiler/src/main/java/dev/w0fv1/norm/language/CompletionContextResolver.java) | 真实接收者、名称替换范围与接入形式；拒绝跨快照位置，不伪造点号或重写捕获文本。 |
| [CompletionEngine](../../cli/compiler/src/main/java/dev/w0fv1/norm/language/CompletionEngine.java) | 显式与无点号调用共用成员候选、可见性与排序。前缀补全不强行形成可执行 AST；已有块不重复插入。 |
| [CallSiteResolver](../../cli/compiler/src/main/java/dev/w0fv1/norm/language/CallSiteResolver.java) | 从真实调用/闭包范围与 ResolvedCall 取得活动回调、泛型替换和参数索引，不另做重载解析。 |

完整调用的 Hover、定义、引用、重命名、错误和语义高亮继续使用真实成员名称范围。TextMate 不决定调用关系，也不把 `then`、`error` 或回调参数硬编码为语言关键字。

## 源码迁移

旧版可能将同行相邻尾随闭包解析为独立语句，新版则可能解析为成员链。新编译器编译通过不足以证明行为不变；保留独立调用的机械迁移是换行或插入分号。

迁移顺序为：固定旧完整分发与源码清单，使用旧 AST 确认语句/结果构建器元素边界，记录候选及必要编辑，最后才用新编译器或格式化器处理。候选定位可以过滤 token，不能全仓正则替换或修改普通字符串内容。

审计范围包含标准库、语言测试、Norm 文档示例、内嵌测试字符串、代码生成模板、实际 Todo/UI 源码，以及选定依赖闭包中的 NAR、本地模块和生成绑定。清单记录来源、源码摘要、候选范围、旧语句关系与处置。

受影响的自有 NAR 从作者态源码重建、分配新包版本并更新锁定；不得改写同坐标归档或完整性缓存。第三方归档尚未升级时，不宣称对应应用迁移完成。未受影响的锁定归档不作无意义重建。审计不进入 ProjectLoader 的日常运行流程。

按[语言演进规则](language-evolution.md)整体交付新的发行版本，不把部分实验 JAR 放入用户 PATH 或扩展。CLI、编辑器和 Native 使用同一完整工具链身份，不建设仅 CLI 生效的兼容模式。

## 实施批次

| 批次 | 边界 |
| --- | --- |
| D0 | 固定旧分发；审计与迁移独立块、自有模板、归档和生成输入；旧版行为基线。 |
| D1 | AST/边界/换行红测，postfix 解析、连接指纹和语义版本一次落地。 |
| D2 | 规范格式化、幂等和行为往返；不附带全仓格式化噪声。 |
| D3 | 成员位置与共享补全、签名帮助、导航、恢复和真实编辑器验收。 |
| D4 | 库示例和 Todo 使用新写法；保留标准任务 API 与生命周期，不回引 ui.async。 |
| D5 | 完整分发、扩展、Todo EXE、分批回归、GUI 与交付 EXE 生命周期验收；核对源码与工具链摘要。 |

各批提交前审查并取得提交许可；中间产物不能替换已交付工具链。构建、测试与验收期间冻结相关源码，禁止多个构建进程同时写同一输出目录。

## 验证索引

| 范围 | 可执行入口 |
| --- | --- |
| S01–S11：调用关联、类型、优先级、求值一次、诊断、extension、Task 与所有权 | [BlockCallChainSyntaxTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/BlockCallChainSyntaxTest.java)、[BlockCallChainExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/BlockCallChainExecutionTest.java)、FunctionCompilerTest、AsyncExecutionTest、TaskExecutionTest。 |
| B01–B08：LF/CRLF/CR、分隔、非调用花括号、控制结构、非支持形式、插值及未完成源码 | BlockCallChainSyntaxTest、SourceRecoveryTest、CompactGuiSyntaxTest。 |
| I01–I04：双向换行编辑、冷/热一致、同名不同输出、非语义空白复用、撤销/重做与缓存 | [IncrementalAnalysisPlanTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/IncrementalAnalysisPlanTest.java)、IncrementalCompilationTest。 |
| F01–F04：规范拼写、独立块、长链/括号和错误文件 | [SourceFormatterTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/SourceFormatterTest.java)。 |
| L01–L04：前缀编辑、导航、回调作用域、签名及会话边界 | [BlockCallChainLanguageTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/language/BlockCallChainLanguageTest.java)、LanguageServiceTest、RenamePreviewTest、LanguageServerTest 和[真实 VS Code 测试](../../cli/extensions/vscode/src/test/extension.test.ts)。 |
| A01：实际发现并运行同步链与任务链 | [functions 程序](../../norm/tests/functions)及[发现规则](../../norm/tests/README.md)。 |
| A02：跨模块/归档及生成源码 | 实际应用加载的完整输入清单、锁定归档摘要和生成绑定源码；不是只搜仓库文本。 |
| A03：Todo JVM / Native 各四阶段 | Todo 仓库 reference-tests/verify.ps1：新增、编辑、完成/筛选、删除、清理、连续输入及两种重启持久化。 |
| A04：交付 EXE | 对同摘要 todo.exe 做隔离启动—关闭—重启—关闭，验证标题与完成状态持久化、应用及启动器退出码 0、无运行异常；check.exe 不能替代。 |
| A05–A08：生命周期、产物身份、日常数据与架构 | Task/异步/取消与清理测试；完整分发和源码 SHA-256；日常 H2 数据库前后摘要不变；DependencyArchitectureTest、AuthoringArchitectureTest。 |

本地分发与格式检查入口见[工具链开发规范](toolchain-development.md#本地验收与测量入口)。工具链选择和完整性核对复用 [select-toolchain.ps1](../../cli/compiler/scripts/select-toolchain.ps1)、[resolve-toolchain.ps1](../../cli/compiler/scripts/resolve-toolchain.ps1) 与 [test-toolchain.ps1](../../cli/compiler/scripts/test-toolchain.ps1)。扩展复用 `npm run test:language`、`npm run check`、`NORM_TEST_GREP` 定向端到端测试及现有打包入口。

Todo 使用相邻 Norm 仓库的新选定分发运行 `build.ps1` 与 `reference-tests/verify.ps1 -Mode all`，随后验证交付 EXE。所有写入使用新建隔离数据库。安装、加载与 LSP 重启须有实际进程和摘要证据，不能只比较展示版本号。

每批记录实际命令、退出码、用例名称与数量、失败/跳过、原始日志/XML、源码身份和产物摘要。只验证工厂方法存在、编译通过或启动窗口不等于行为验收通过。本机 Maven/NAR 缓存命中应单列记录，不冒充冷缓存构建或其他平台发布矩阵。性能结论须单独测量，不从语法糖推断零开销或提速。
