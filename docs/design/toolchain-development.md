# 工具链开发规范

本规范约束 Norm 官方 Java 工具链的代码组织、依赖方向和执行后端。技术栈选择见[实现策略决议](/design/implementation-strategy)，语言行为由语言规范定义。

## 仓库边界

```text
tool/core/             编译前端与 canonical Core
tool/execution-api/    后端无关的执行契约
tool/project-system/   标准库引导、模块配置与项目启动
tool/truffle-backend/  Core lowering 与 Truffle 执行
tool/cli/app/          命令行与 Language Server 生命周期
tool/cli/extensions/   编辑器扩展
norm/stdlib/           使用 Norm 编写的标准库
norm/tests/            可执行的 Norm 验收程序
```

Gradle 模块按编译、执行契约、项目生命周期、后端和宿主工具分层。跨层数据只使用下层公开的强类型模型。

## Core package

```text
dev.w0fv1.norm.frontend     Compiler、Lexer、Parser、Analyzer
dev.w0fv1.norm.syntax       Token 与 Syntax AST
dev.w0fv1.norm.semantic     类型、符号与文档语义索引
dev.w0fv1.norm.builtin      内置声明与 intrinsic identity
dev.w0fv1.norm.bound        前端内部 resolved representation
dev.w0fv1.norm.core         content-addressed Core IR 与依赖索引
dev.w0fv1.norm.core.store   canonical definition 内容存储
dev.w0fv1.norm.diagnostic   诊断值与渲染
dev.w0fv1.norm.language     基于语义快照的语言服务
dev.w0fv1.norm.value        跨阶段不可变数据
```

`execution-api` 提供 `ExecutionBackend`、`ExecutionContext` 和结构化运行错误。`project-system` 提供 `ProjectEnvironment`、`ProjectLoader` 和 `ProjectLauncher`。`truffle-backend` 保存 Lowerer、可执行节点和运行时表示。

必须保持的阶段依赖约束为：

```text
frontend ⇏ truffle
core ⇏ frontend, truffle
Lowerer → core
project-system → execution-api → core
truffle-backend → project-system, execution-api, core
CLI → project-system, truffle-backend
```

`⇏` 表示禁止依赖。`bound` 只在前端内部完成已解析语义到 Core 的转换。Lowerer 只消费 Core，不依赖 Syntax AST、`SemanticModel` 或 `bound`。CLI 不访问内部 Truffle 节点。新增 package 时按领域归属放置，不能为绕开依赖约束复制类型或语义表。

## CLI package

```text
dev.w0fv1.norm.cli              JVM 入口
dev.w0fv1.norm.cli.controller   命令解析、路由与执行
dev.w0fv1.norm.cli.component    版本与 Language Server 组件
dev.w0fv1.norm.cli.value        CLI 公共数据
dev.w0fv1.norm.cli.utils        无状态文本工具
```

只有 `Main` 可以终止 JVM。Controller 通过返回退出码报告结果，component 不读取命令行参数。

编辑器能力以 `core` 的 `LanguageService` 和不可变语义快照为唯一语义实现。补全排序、期望类型、泛型替换、调用参数和导入候选均在 `dev.w0fv1.norm.language` 中计算；Language Server 只负责 LSP 类型转换，编辑器扩展只负责生命周期和编辑器接入。

## 命名与可见性

- `dev.w0fv1.norm` 已经提供语言上下文，类型名不增加 `Norm` 前缀；使用 `Compiler`、`Analyzer`、`Lowerer`、`ProgramRunner` 等领域名称。
- 对外 API 才使用 `public`。Lexer、Parser、Analyzer、Truffle 节点和运行时表示保持模块内部可见。
- `value` 只存放跨阶段不可变数据；具有明确领域的数据保留在对应领域，例如 Syntax AST 属于 `syntax`。
- `utils` 只接受静态、无状态、可独立复用的工具。生命周期、I/O 和可变状态不进入 `utils`。
- 同一概念只保留一个模型，禁止并行维护旧 AST、临时 IR 或第二条执行链。

## 编译与执行阶段

```text
SourceFile
  → Lexer
  → Token
  → Parser
  → Syntax.Program
  → Analyzer
  → SemanticModel
  → Binder
  → CoreBuilder
  → CoreCanonicalizer
  → DefinitionStore
  → CoreCompilation
  → Lowerer
  → Truffle executable AST
```

Parser 只建立语法结构。Analyzer 负责名称、类型和控制流检查。Binder 冻结已验证语义，CoreBuilder 分离 canonical definition 与 authoring occurrence metadata，CoreCanonicalizer 计算递归组和固定依赖的内容身份。Lowerer 只把 `CoreCompilation` 转换成可执行表示。完整身份与阶段边界见[编译器架构](/spec/compiler-design)。

一个项目分析产生不可变 `CompilationSnapshot`。诊断和语言能力使用同一 `SemanticModel`、`SpanIndex` 与 `ReferenceIndex` 的文档视图；`CompilationEnvironment` 复用未变化的解析结果和标准库 prelude，新文档修订以原子方式替换快照。

`ProjectLauncher` 与 Polyglot Source 执行共享 `CompilerSession → CompilationOutput → TruffleExecutionBackend` 链路。每个函数对应独立的 `FunctionRootNode` 和 `CallTarget`。静态函数与方法调用使用 `DirectCallNode`，局部变量使用 `VirtualFrame` 的索引 slot，循环使用 `LoopNode`，return、break 和 continue 使用 `ControlFlowException`。执行上下文通过隐藏根参数传递，源码位置由 `CoreAuthoringMap` 中的精确 occurrence origin 附加到 Truffle 节点。

`@TruffleBoundary` 只允许出现在宿主 I/O 等慢路径，不能包围 guest-language 计算。值复制语义集中在运行时表示中；如改为 copy-on-write，不得改变语言可观察行为。

## 测试

- 先写或迁移失败测试，再修改实现。
- 单元测试与被测 package 对齐，内部组件不因测试而扩大可见性。
- 语法或执行变更必须覆盖诊断测试，以及 `norm/tests` 中的单文件和模块程序。
- Java 修改先运行相关模块测试；提交前执行格式检查。发布前才运行完整发布验证。
- 后端变更必须通过 Polyglot 注册入口和 CLI 的真实 `.norm` 文件执行测试。

验收测试的领域、目录、命名、发现入口与运行命令统一由 [`norm/tests/README.md`](https://github.com/w0fv1/norm/blob/main/norm/tests/README.md) 定义。

## 文档同步

语言行为修改语言规范；实现结构修改本规范；技术栈决策修改实现策略决议。其他页面只链接这些入口，不复制规则。
