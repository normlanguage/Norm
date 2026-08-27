# 编译器架构

Norm 官方编译器使用 Java 实现。`.norm` 源码是项目的 authoring source，名称解析和类型检查之后生成确定性的 content-addressed Core IR；Truffle 是 Core 的唯一执行后端。技术栈由[实现策略决议](/design/implementation-strategy)固定，工程依赖规则见[工具链开发规范](/design/toolchain-development)。

```text
ProjectEnvironment
  → module.norm + hidden entry → Core → Truffle → ModuleDescriptor
  → ProjectSourceSet
  → Lexer / Parser
  → Analyzer / SemanticModel ──→ authoring snapshot
  → Binder
  → CoreBuilder
  → CoreCanonicalizer
  → DefinitionStore
  → CompilationOutput
  → Lowerer
  → Truffle CallTarget
```

## 项目与前端

`ProjectEnvironment` 先用 bootstrap 协议求值标准库的 `module.norm`，再建立共享标准库 prelude。`ProjectLoader` 递归求值 `Module module()` 返回的精确依赖图，并建立不可变 `ProjectSourceSet`。模块配置与业务程序分别编译，配置 artifact 不进入业务 Core 依赖图。CLI、Language Server、Polyglot 入口和测试工具共享 `tool/project-system` 中的生命周期。`CompilationScope` 统一携带每个源码文档的模块名、版本、相对路径和模块直接读取边，Analyzer 与语言服务共同使用这一个可见性模型。模块规则见[模块系统](/spec/module-system)。

Lexer 与 Parser 建立带源码位置的语法树。Analyzer 先登记完整类型头，再解析成员与 callable，在完整项目声明图上完成名称解析、类型检查、泛型推断、interface 关系与见证验证、switch 穷尽检查、nullable 流分析、确定赋值和调用绑定，并产生不可变 `SemanticModel`。普通调用由 `ResolvedCall` 保存精确目标、实例化形参、类型实参、实参映射与结果类型；interface requirement 与实现关系同样只在语义模型中保存一份。Binder、签名帮助、导航和引用索引共同读取这份结果。编辑器诊断、补全、悬停、导航、引用和重命名始终读取同一修订的语义快照；authoring snapshot 到此结束，执行编译才物化 Core。

`Binder` 将已验证语义冻结为内部 resolved representation。全局调用目标、interface requirement 与 witness、字段 owner 与 ordinal、实参到形参映射、源码求值顺序、闭包目标与捕获、运行时泛型实参和 value/identity 复制语义在这里固定；后续阶段直接使用确定目标。

## 身份边界

| 身份 | 所属世界 | 作用 |
| --- | --- | --- |
| `DocumentId` / `SymbolId` | authoring | 文档修订、诊断和编辑器操作 |
| `DefinitionId` | semantic Core | 不可变定义及其固定依赖 |
| `CoreNamespaceId` | namespace | 名字、可见性、导出和公开签名 |
| `ArtifactId` | backend | Core 程序、调试来源和后端 ABI 对应的可执行产物 |

`DefinitionId` 只来自版本化 canonical encoding。可调用定义的名字、参数名、局部变量名、源码位置和空白位于 semantic Core 之外；局部绑定与类型参数使用定义内的稠密索引。

内置类型由稳定的 `BuiltinTypeId` 标识，用户类型由 `CoreDefinitionLink` 标识。名义类型键包含模块名、模块版本、package、类型名和可见性；private 类型额外包含模块相对源码路径。类型改名或在模块内移动 private 类型会产生新的名义身份，移动整个项目根目录不会改变身份。class/value aggregate 的类别、泛型参数、父类型、字段布局、构造入口、方法分派和 interface conformances，interface 的泛型参数、父接口与 requirements，以及 enum variant 的稳定键与 payload 类型都属于语义内容。

`CoreNamespace` 保存 authoring 名字、签名、可见性、导出状态与精确 occurrence。`CoreAuthoringMap` 为每个 canonical definition 保存按来源稳定编号的 `DefinitionOccurrenceId`、`CoreDefinitionOrigin` 和引用 occurrence 路由。Lowerer 按调用所在 occurrence 选择对应来源，因此共享同一 `DefinitionId` 的多个源码定义仍保留各自的名字、位置和调用栈。

## Canonical Core

`CoreBuilder` 把 resolved representation 转成强类型 `CoreDefinition`。callable、aggregate、enum、interface、interface method 与 builtin conformance 使用同一内容定义模型；调用、构造、enum variant、interface witness、用户类型和字段 owner 都先成为 `PendingDefinitionReference`。`CoreCanonicalizer` 遍历签名、泛型 bound、interface 关系、局部类型、运行时类型、字段和可执行表达式建立完整依赖图，并对强连通分量进行规范化：分量内引用使用成员索引，分量外引用使用完整 `DefinitionId`。整个递归组由 `DefinitionGroupId` 标识，成员由 group identity 与规范成员索引标识。

`CoreCodec` 是 canonical bytes 的唯一编码入口。当前身份边界使用 `CoreSchemaVersion.V7` 与 `LanguageSemanticsVersion.V7`；编码固定版本、域分隔、节点 tag、字节序、集合顺序和字符串编码，Java 对象序列化、Truffle AST 与运行期 profile 不参与语义哈希。

`CoreProgram` 在内容进入存储前验证完整闭包：名义类型与泛型 bound、callable receiver 与 reified ABI、interface 继承和完整 witness、局部和运行时类型、调用与构造目标、字段和 enum 引用、内建协议与操作契约及 namespace binding 必须彼此一致。运行时类型 capture 按类型参数索引规范排序，因此执行语义相同的 descriptor 只有一种 canonical encoding。

标准库源码经过同一条 Core 管线，并使用 `module.norm` 提供的模块坐标。`DefinitionStore` 按完整内容哈希保存 canonical group，内存实现用于隔离编译会话，文件实现用于 CLI 的跨进程内容复用。存储写入返回强类型的 stored、reused 或 not-admitted 结果；超出策略上限的对象不会落盘。文件读取验证 identity，并区分内容缺失与内容损坏；写入在固定锁分片内重检，持久化临时内容后原子发布并复验。根目录持久化存储策略并由维护锁保护，清理以文件系统重扫结果为事实来源。内容缓存按组数和字节数保持有界；authoring snapshot 不访问内容存储。

## 增量边界

`CompilerSession` 按文档内容复用解析结果，并按稳定的 `CompilationUnitId` 保留前一份 `CompilationOutput`。定义依赖被编码进 identity，因此修改叶子定义会为其依赖闭包产生新 identity，而不相关定义继续复用原有 identity 和内容组。模块以根 `module.norm` 的 URI 标识编译单元，独立文件以自身 URI 标识。

`CoreDependencyIndex` 提供直接依赖、反向依赖和传递 dependents；`CoreCompilationDelta` 给出新增、复用和脱离当前 source set 的定义。类型引用与可执行引用使用同一依赖传播规则。下游缓存以这些强类型 identity 作为失效边界，内容存储命中只做完整性校验和读取。

## Truffle 后端

`TypedProgram` 和 `ExecutionBackend` 只暴露 `CoreCompilation`。Lowerer 只消费已解析 Core，生成函数 `CallTarget`、frame slot、控制流节点、固定目标调用和按静态方法或 interface requirement `DefinitionId` 索引的分发表。class 与 interface 调用共用动态分派入口；遍历式 `for` 通过 `Iterable<T>` 和 `Iterator<T>` requirements 工作，内建集合返回内部 `NativeIterator<T>` 运行时值。

`ExecutionContext` 作为隐藏根参数沿固定调用边传递，可执行节点不捕获单次运行状态。`TruffleExecutionBackend` 以 `ArtifactId` 在有界缓存中保存上下文无关的可执行程序；artifact identity 覆盖 Core groups、入口 occurrence、namespace、binding occurrence、源码 URI 与内容、origin span、引用 occurrence 路由和后端 ABI。Polyglot 入口在执行时取得当前 language context，因此同一 artifact 可以安全服务多个执行上下文。

guest 运行错误在 Truffle 节点处携带稳定错误码和 `SourceSection`，跨公开边界后转换为结构化 `NormExecutionException`。Native Image 打包同一 Core 与 Truffle 执行链。

## 验证

身份测试覆盖源码移动、泛型 alpha rename、显式与推断类型实参、模块版本、名义类型、声明重排、递归 SCC、类型依赖传播和 authoring occurrence 路由。存储测试覆盖准入策略、只读校验、损坏恢复、并发发布、并发清理与跨实例读取。边界测试覆盖 Core 类型与操作 ABI、namespace shape 和重复 group；后端测试覆盖 Core-only 依赖、DefinitionId 枚举身份、artifact 复用、独立执行上下文、Polyglot 入口、源码位置和 guest 调用栈。
