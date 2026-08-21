# 工具链开发规范

本规范约束 Norm 官方 Java 工具链的代码组织、依赖方向和执行后端。技术栈选择见[实现策略决议](/design/implementation-strategy)，语言行为由语言规范定义。

## 仓库边界

```text
tool/core/             编译前端、诊断、执行入口与 Truffle 后端
tool/cli/app/          命令行与 Language Server 生命周期
tool/cli/extensions/   编辑器扩展
norm/stdlib/           使用 Norm 编写的标准库
norm/tests/            可执行的 Norm 验收程序
```

`core` 不拆成 compiler、runtime 和 truffle Gradle 模块。它们共享同一份语法、类型信息和源码位置，避免跨模块复制模型。

## Core package

```text
dev.w0fv1.norm.frontend     Compiler、Lexer、Parser、Analyzer
dev.w0fv1.norm.syntax       Token 与 Syntax AST
dev.w0fv1.norm.diagnostic   诊断值与渲染
dev.w0fv1.norm.execution    对外执行入口
dev.w0fv1.norm.truffle      Lowerer、可执行节点与运行时表示
dev.w0fv1.norm.value        跨阶段不可变数据
dev.w0fv1.norm.utils        无状态公共工具
```

允许的主要依赖方向为：

```text
frontend  → syntax, diagnostic, value
execution → truffle, value
truffle   → frontend, syntax, value
diagnostic → value
```

`frontend` 不依赖 Truffle。`syntax` 和 `value` 不依赖编译器行为。CLI 只调用 core 导出的 API，不访问内部 Truffle 节点。

## CLI package

```text
dev.w0fv1.norm.cli              JVM 入口
dev.w0fv1.norm.cli.controller   命令解析、路由与执行
dev.w0fv1.norm.cli.component    版本与 Language Server 组件
dev.w0fv1.norm.cli.value        CLI 公共数据
dev.w0fv1.norm.cli.utils        无状态文本工具
```

只有 `Main` 可以终止 JVM。Controller 通过返回退出码报告结果，component 不读取命令行参数。

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
  → TypedProgram
  → Lowerer
  → Truffle executable AST
  → ProgramRunner
```

Parser 只建立语法结构。Analyzer 负责名称、类型和控制流检查。Lowerer 将已经检查的程序转换成可执行表示，运行时不得重新按名称解析声明或解释 Syntax AST。

每个函数对应独立的 `FunctionRootNode` 和 `CallTarget`。静态函数与方法调用使用 `DirectCallNode`，局部变量使用 `VirtualFrame` 的索引 slot，循环使用 `LoopNode`，return、break 和 continue 使用 `ControlFlowException`。Truffle 节点携带 `SourceSection`。

`@TruffleBoundary` 只允许出现在宿主 I/O 等慢路径，不能包围 guest-language 计算。值复制语义集中在运行时表示中；如改为 copy-on-write，不得改变语言可观察行为。

## 测试

- 先写或迁移失败测试，再修改实现。
- 单元测试与被测 package 对齐，内部组件不因测试而扩大可见性。
- 语法或执行变更必须覆盖诊断测试和 `norm/tests` 中的单文件程序。
- Java 修改先运行相关模块测试；提交前执行格式检查。发布前才运行完整发布验证。
- 后端变更必须通过 Polyglot 注册入口和 CLI 的真实 `.norm` 文件执行测试。

常用命令：

```powershell
.\gradlew.bat :core:spotlessApply :core:test
.\gradlew.bat :cli:test
.\gradlew.bat :cli:run --args="run docs/examples/hello.norm"
```

## 文档同步

语言行为修改语言规范；实现结构修改本规范；技术栈决策修改实现策略决议。其他页面只链接这些入口，不复制规则。
