# 编译器架构

Norm 官方编译器使用 Java 实现。`.norm` 源码是项目的 authoring source，名称解析和类型检查之后生成确定性的 content-addressed Core IR；Truffle 是 Core 的唯一执行后端。技术栈由[实现策略决议](/design/implementation-strategy)固定，工程依赖规则见[工具链开发规范](/design/toolchain-development)。

`dev.w0fv1.norm.abi` 是前端、Core 与后端共享的无状态叶子层。`cli/compiler/stdlib-abi.json` 是 intrinsic、异常布局和运行时 shape 的声明式来源，构建生成 Java ABI 与指纹；semantic 不依赖 Core 或 builtin catalog，builtin 通过 `BuiltinSemanticIndex` 向语义快照提供只读成员视图。

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

`ProjectEnvironment` 先用 bootstrap 协议求值标准库的 `module.norm`，再建立共享标准库 prelude。`ProjectLoader` 递归求值 `Module module()` 返回的精确依赖图，并建立不可变 `ProjectSourceSet`。模块配置与业务程序分别编译，配置 artifact 不进入业务 Core 依赖图。CLI、Language Server、Polyglot 入口和测试工具共享 `compiler` 的 `project` package 中的生命周期。`CompilationScope` 统一携带每个源码文档的模块名、版本、相对路径和模块直接读取边，Analyzer 与语言服务共同使用这一个可见性模型。模块规则见[模块系统](/spec/module-system)。

Lexer 与 Parser 建立带源码位置的语法树。语义流水线先从全部语法树构造只读声明索引，再以不可变 `SemanticAnalysisInput` 建立一次分析独占的 `SemanticAnalysisContext`。`ImportResolver` 返回 alias 与绑定增量，`VisibilityResolver` 只读构造文件作用域和可导出符号；类型关系、表达式/调用、注解与构造器流分别由组合式 `TypeSystem`、`ExpressionChecker`、`AnnotationChecker` 和 `ConstructorFlowAnalyzer` 处理。完整项目分析产生不可变 `SemanticModel`；普通调用由 `ResolvedCall` 保存精确目标、实例化形参、类型实参、实参映射与结果类型。Binder、签名帮助、导航和引用索引共同读取这份结果，authoring snapshot 到此结束，执行编译才物化 Core。

`Binder` 将已验证语义冻结为内部 resolved representation。全局调用目标、interface requirement 与 witness、字段 owner 与 ordinal、实参到形参映射、源码求值顺序、闭包目标与捕获、运行时泛型实参和 value/identity 复制语义在这里固定；后续阶段直接使用确定目标。

## 身份边界

| 身份 | 所属世界 | 作用 |
| --- | --- | --- |
| `DocumentId` / `SymbolId` | authoring | 文档修订、诊断和编辑器操作 |
| `DefinitionId` | semantic Core | 不可变定义及其固定依赖 |
| `PublicAbiId` | namespace | 导出的名字、可见性和公开签名 |
| `CoreCodeId` / `MetadataId` | executable | Core 代码与运行期 companion metadata |
| `DebugInfoId` | authoring | 源码、位置和 occurrence 路由 |
| `ExecutableId` | backend | 代码、运行期 metadata 与后端 ABI |
| `ArtifactId` | bundle | 代码、链接、公开 ABI、调试信息和 metadata 的发布组合 |

`DefinitionId` 只来自版本化 canonical encoding。参数名属于可观察的调用与 `ParameterContext` 契约；可调用定义的名字、局部变量名、源码位置和空白位于 semantic Core 之外。局部绑定与类型参数使用定义内的稠密索引。

作者态声明身份由文档、声明类别、名称和规范签名构成，不包含声明序号与源码偏移。成员身份从 owner 派生；重载解析族按可见性作用域归组，public 声明可跨文档属于同一族，private 声明保持文档局部。

内置类型由稳定的 `BuiltinTypeId` 标识，用户类型由 `CoreDefinitionLink` 标识。名义类型键包含模块名、模块版本、package、类型名和可见性；private 类型额外包含模块相对源码路径。类型改名或在模块内移动 private 类型会产生新的名义身份，移动整个项目根目录不会改变身份。class/value aggregate 的类别、泛型参数、父类型、字段布局、构造入口、方法分派和 interface conformances，interface 的泛型参数、父接口与 requirements，以及 enum variant 的稳定键与 payload 类型都属于语义内容。

`CoreNamespace` 保存 authoring 名字、签名、可见性、导出状态与精确 occurrence。`CoreAuthoringMap` 为每个 canonical definition 保存按来源稳定编号的 `DefinitionOccurrenceId`、声明 role、`CoreDefinitionOrigin` 和引用 occurrence 路由。`CoreArtifact.metadata` 保存以 occurrence 为目标的 companion metadata。Lowerer 按调用所在 occurrence 选择对应来源，因此共享同一 `DefinitionId` 的多个源码定义仍保留各自的角色、名字、位置、调用栈和注解。

## Canonical Core

`CoreBuilder` 把 resolved representation 转成强类型 `CoreDefinition`。callable、aggregate、enum、interface、interface method 与 builtin conformance 使用同一内容定义模型；调用、构造、enum variant、interface witness、用户类型和字段 owner 都先成为 `PendingDefinitionReference`。`CoreCanonicalizer` 遍历签名、泛型 bound、interface 关系、局部类型、运行时类型、字段和可执行表达式建立完整依赖图，并对强连通分量进行规范化：分量内引用使用成员索引，分量外引用使用完整 `DefinitionId`。整个递归组由 `DefinitionGroupId` 标识，成员由 group identity 与规范成员索引标识。

Bound 到 Core 的转换由 `BoundCoreBodyConverter` 对 sealed hierarchy 进行穷尽匹配，新增节点必须同时完成转换才能通过编译；Core traversal fixture 约束 codec、walker 与 rewriter 的覆盖。

规范化 refinement 同时使用带位置的出边和入边结构，并跳过已证明属于同一 automorphism 的搜索分支。搜索预算保留为对抗性图的资源边界；component 大小、refinement、搜索、memo 和 automorphism 剪枝统一进入 `CoreBuildReport`。

`CoreCodec` 是 canonical bytes 的唯一编码入口。当前身份边界使用 `CoreSchemaVersion.V11` 与 `LanguageSemanticsVersion.V11`；编码固定版本、域分隔、节点 tag、字节序、集合顺序和字符串编码，Java 对象序列化、Truffle AST 与运行期 profile 不参与语义哈希。

`CoreProgram` 在内容进入存储前验证完整闭包：名义类型与泛型 bound、callable receiver 与 reified ABI、interface 继承和完整 witness、局部和运行时类型、调用与构造目标、字段和 enum 引用、内建协议与操作契约及 namespace binding 必须彼此一致。运行时类型 capture 按类型参数索引规范排序，因此执行语义相同的 descriptor 只有一种 canonical encoding。

标准库源码经过同一条 Core 管线，并使用 `module.norm` 提供的模块坐标。`DefinitionStore` 按完整内容哈希保存 canonical group，内存实现用于隔离编译会话，文件实现用于 CLI 的跨进程内容复用。存储写入返回强类型的 stored、reused 或 not-admitted 结果；超出策略上限的对象不会落盘。文件读取验证 identity，并区分内容缺失与内容损坏；写入只持有对应哈希分片的锁，持久化临时内容后原子发布并复验。容量协调在发布后按根目录串行，以文件系统快照为事实来源执行回收；不同分片与不同存储根目录保持独立。内容缓存按组数和字节数保持有界；authoring snapshot 不访问内容存储。

## 增量边界

`CompilerSession` 按文档内容复用解析结果，并按稳定的 `CompilationUnitId` 保留前一份 `CompilationOutput`。定义依赖被编码进 identity，因此修改叶子定义会为其依赖闭包产生新 identity，而不相关定义继续复用原有 identity 和内容组。模块以根 `module.norm` 的 URI 标识编译单元，独立文件以自身 URI 标识。

声明级分析以词法结构而非绝对偏移判断变化。空白编辑和声明重排通过 token anchor 将复用贡献映射到当前源码；声明新增、删除或签名族变化只失效对应解析族及其语义 dependents，package、import 或编译作用域变化仍按文档边界失效。

同一编译单元的操作按历史顺序串行提交，不同编译单元可并行分析和构建。解析缓存与编译历史只在短状态锁内读写；invalidate 和 close 使用独占生命周期边界，不与在途编译交错。

`CoreDependencyIndex` 提供直接依赖、反向依赖和传递 dependents；`CoreCompilationDelta` 给出新增、复用和脱离当前 source set 的定义。类型引用与可执行引用使用同一依赖传播规则。下游缓存以这些强类型 identity 作为失效边界，内容存储命中只做完整性校验和读取。

## Truffle 后端

`TypedProgram` 和 `ExecutionBackend` 只暴露 `CoreCompilation`。Lowerer 只消费已解析 Core，生成函数 `CallTarget`、frame slot、控制流节点、固定目标调用和按静态方法或 interface requirement `DefinitionId` 索引的分发表。class 与 interface 调用共用动态分派入口；遍历式 `for` 通过 `Iterable<T>` 和 `Iterator<T>` requirements 工作，内建集合返回内部 `NativeIterator<T>` 运行时值。

`ExecutionContext` 作为隐藏根参数沿固定调用边传递，可执行节点不捕获单次运行状态。standalone `TruffleExecutionBackend` 以 `ExecutableId` 在有界缓存中保存上下文无关的可执行程序，空白、位置和源码 URI 变化只改变 `DebugInfoId`；运行错误通过当前 artifact 的 authoring sidecar 映射位置。需要 Truffle source instrumentation 的 Polyglot 路径把 `DebugInfoId` 纳入实例化边界。内建 ABI 指纹属于后端 ABI key。

guest 运行错误在 Truffle 节点处携带稳定错误码和 `SourceSection`，跨公开边界后转换为结构化 `NormExecutionException`。Native Image 打包同一 Core 与 Truffle 执行链。

## 验证

身份测试覆盖源码移动、泛型 alpha rename、显式与推断类型实参、模块版本、名义类型、声明重排、递归 SCC、类型依赖传播和 authoring occurrence 路由。存储测试覆盖准入策略、只读校验、损坏恢复、并发发布、并发清理与跨实例读取。边界测试覆盖 Core 类型与操作 ABI、namespace shape 和重复 group；后端测试覆盖 Core-only 依赖、DefinitionId 枚举身份、artifact 复用、独立执行上下文、Polyglot 入口、源码位置和 guest 调用栈。
