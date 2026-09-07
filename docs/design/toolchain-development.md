# 工具链开发规范

本规范约束 Norm 官方 Java 工具链的代码组织、依赖方向和执行后端。技术栈选择见[实现策略决议](/design/implementation-strategy)，语言行为由语言规范定义。

## 仓库边界

```text
cli/                  命令行产品
  compiler/           Java 编译器、执行运行时、CLI 与 Language Server
  extensions/         编辑器扩展
norm/stdlib/           使用 Norm 编写的标准库
norm/tests/            可执行的 Norm 验收程序
```

`compiler` 是唯一 Gradle 与 JPMS 模块。领域 package 负责分层，跨层数据只使用下层拥有的强类型模型；架构测试禁止逆向依赖。

## 领域边界

| Package | 职责 |
| --- | --- |
| `source` / `syntax` | 源码身份、位置与语法模型 |
| `abi` / `pattern` | 中立运行契约与模式覆盖算法 |
| `semantic` / `builtin` | 语义模型与内建契约的语义投影 |
| `frontend` / `bound` | 分析、已解析语义与 Core 构建 |
| `core` / `core.store` | 内容寻址定义、制品与存储 |
| `project` | 项目发现、模块解析与输入快照 |
| `application` | 应用编译产物、执行准备与资源所有权 |
| `language` / `workspace` | 快照查询、项目分析调度与版本发布 |
| `jvm` | Java 类型投影、绑定规划与注解处理 |
| `execution` / `platform` | 执行与宿主能力契约 |
| `truffle` / `polyglot` | Core 执行实现与 Polyglot 接入 |
| `diagnostic` / `value` | 诊断与其余跨阶段值 |

[DependencyArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/DependencyArchitectureTest.java) 是依赖方向的可执行真相源，包含包级无环约束。`bound` 只由前端消费；Core 不依赖前端语义或内建目录；`project`、`jvm` 和 `truffle` 不反向依赖应用编排。完整阶段和生命周期见[编译器架构](/spec/compiler-design)。

## CLI package

```text
dev.w0fv1.norm.cli              JVM 入口
dev.w0fv1.norm.cli.controller   命令解析、路由与执行
dev.w0fv1.norm.cli.component    版本与 Language Server 组件
dev.w0fv1.norm.cli.value        CLI 公共数据
dev.w0fv1.norm.cli.utils        无状态文本工具
```

只有 `Main` 可以终止 JVM。Controller 通过返回退出码报告结果，component 不读取命令行参数。

编辑器能力以 `language.LanguageService` 和不可变语义快照为唯一语义实现。补全排序、期望类型、泛型替换、调用参数和导入候选均在 `dev.w0fv1.norm.language` 中计算；Workspace 管理项目分析与诊断发布，Language Server 只负责 LSP 类型转换，编辑器扩展只负责生命周期和编辑器接入。

## 命名与可见性

- `dev.w0fv1.norm` 已经提供语言上下文，类型名不增加 `Norm` 前缀；使用 `Compiler`、`Analyzer`、`Lowerer`、`ApplicationRunner` 等领域名称。
- 只有真实的跨进程或扩展契约才形成对外 API。Lexer、Parser、Analyzer、Truffle 节点和运行时表示保持模块内部可见。
- `value` 只存放跨阶段不可变数据；具有明确领域的数据保留在对应领域，例如 Syntax AST 属于 `syntax`。
- `utils` 只接受静态、无状态、可独立复用的工具。生命周期、I/O 和可变状态不进入 `utils`。
- 同一概念只保留一个模型，禁止并行维护旧 AST、临时 IR 或第二条执行链。

## 编译与执行阶段

阶段、产物及所有权以[编译器架构](/spec/compiler-design)和其中的代码入口为准。应用入口使用 `ApplicationCompiler`、`CompiledApplication` 与 `ApplicationRunner`；编辑器入口使用 `Workspace`。调用方关闭自己持有的应用产物，每次运行拥有独立的运行资源。

`ResolvedCall` 是已解析调用的单一结果，语言服务和绑定阶段复用它；尚未完成的类型输入使用 `TypeSyntaxParser`，不能另写类型语法。内建签名只声明在 `stdlib-abi.json`，语义对象与 Core 校验契约均从它派生。

值表示、复制、相等性与哈希归属 `RuntimeValues`；调用准备归属 `RuntimeInvocation`，Unicode 文本操作归属 `RuntimeText`。Truffle 节点不能捕获单次运行的外部资源。系统资源契约见[系统运行时架构](/design/system-runtime)。

## 测试

- 先写或迁移失败测试，再修改实现。
- 单元测试与被测 package 对齐，内部组件不因测试而扩大可见性。
- 语法或执行变更必须覆盖诊断测试，以及 `norm/tests` 中的单文件和模块程序。
- Java 修改先运行相关 package 测试；提交前执行格式检查。发布前才运行完整发布验证。
- 后端变更必须通过 Polyglot 注册入口和 CLI 的真实 `.norm` 文件执行测试。

验收测试的领域、目录、命名、发现入口与运行命令统一由 [`norm/tests/README.md`](https://github.com/normlanguage/Norm/blob/main/norm/tests/README.md) 定义。

## 文档同步

语言行为修改语言规范；实现结构修改本规范；技术栈决策修改实现策略决议。其他页面只链接这些入口，不复制规则。
