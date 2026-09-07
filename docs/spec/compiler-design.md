# 编译器架构

Norm 官方编译器使用 Java 实现。`.norm` 源码是项目的 authoring source，名称解析和类型检查之后生成确定性的 content-addressed Core IR；Truffle 是 Core 的唯一执行后端。技术栈由[实现策略决议](/design/implementation-strategy)固定，工程依赖规则见[工具链开发规范](/design/toolchain-development)。

`dev.w0fv1.norm.abi` 是前端、Core 与后端共享的无状态叶子层。[`stdlib-abi.json`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/stdlib-abi.json) 是内建类型、调用签名、能力、异常布局和运行时 shape 的单一声明源。构建生成中立契约与指纹，`BuiltinContracts` 派生校验视图，`BuiltinSemanticView` 派生语义视图。Core 不依赖 semantic 或 builtin；通用模式覆盖算法归属 `pattern`，源码身份与位置归属 `source`。

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
  → CompilationOutput.artifact
  → ApplicationCompiler → CompiledApplication
  → Lowerer
  → Truffle CallTarget
```

## 项目与前端

`ProjectEnvironment` 先用 bootstrap 协议求值标准库的 `module.norm`，再建立共享标准库 prelude。`ProjectLoader` 递归求值 `Module module()` 返回的精确依赖图，并建立不可变 `ProjectSourceSet`。模块配置与业务程序分别编译，配置 artifact 不进入业务 Core 依赖图。项目发现与输入捕获归属 `project`；CLI 与 Polyglot 共享 `application` 的编译和资源准备，Language Server 通过 `workspace` 管理文档分析。`CompilationScope` 统一携带每个源码文档的模块名、版本、相对路径和模块直接读取边，Analyzer 与语言服务共同使用这一个可见性模型。模块规则见[模块系统](/spec/module-system)。

Lexer 与 Parser 建立语法树，语言服务的类型片段也通过 `TypeSyntaxParser` 进入同一语法实现。补全上下文直接消费 Lexer 的字面量、插值与未闭合字面量 token。`Analyzer` 只编排声明、类型与函数体分析；`SemanticModelBuilder` 是语义输出的唯一写入口，`TypeResolutionState` 与 `BodyAnalysisState` 分别拥有类型解析游标和函数体流状态。试探分析通过完整 checkpoint 回滚；循环流状态合并统一归属 `FlowAnalyzer`。

`CallResolver` 统一普通函数、方法、接口、构造器、内建函数与枚举构造的候选推断和选择，`CallArguments` 负责实参映射，`TypeArguments` 负责默认类型参数补全。Diamond 构造与所属类型参数共同参与候选求解；候选适用性和最终参数校验共用类型关系，行为约束见 [SemanticProbeTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/SemanticProbeTest.java)。`ResolvedCall` 保存精确目标与实例化签名，Binder、签名帮助和导航读取这份结果。分析阶段的边界由 [AnalyzerTypeArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/AnalyzerTypeArchitectureTest.java) 约束。

`TypeRelations.DeclarationGraph` 统一名义类型投影、赋值关系与共同类型求解，前端提供声明关系，`SemanticModel` 提供冻结后的关系；两者通过 `TypeApplication` 解析类型应用，泛型推断共用 `TypeConstraintSolver`。内建协议查询由 `BuiltinCatalog` 实例化 ABI 契约。语义约束见 [SemanticArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/SemanticArchitectureTest.java)。`MemberRelations` 从 override 与 witness 派生成员关系，导航、引用与重命名共用这份关系；编辑器行为约束见 [AuthoringArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/language/AuthoringArchitectureTest.java)。

`Workspace` 按项目管理分析批次，`AnalysisScheduler` 合并待处理修改并取消过期任务。项目内所有打开文档原子发布同一快照；标准库源码的身份转换与完整 overlay 由 `ProjectSession` 准备，批次约束见 [WorkspaceTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/workspace/WorkspaceTest.java)。分析提交和诊断发送均校验文档版本及批次归属。LSP `DocumentService` 只转换协议数据。状态和调度入口见 [workspace](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/main/java/dev/w0fv1/norm/workspace)。

`Binder` 将已验证语义冻结为内部 resolved representation。全局调用目标、interface requirement 与 witness、字段 owner 与 ordinal、实参到形参映射、源码求值顺序、闭包目标与捕获、运行时泛型实参和 value/identity 复制语义在这里固定；后续阶段直接使用确定目标。

## 应用与制品边界

`ApplicationCompiler` 返回可关闭的 `ApplicationCompilation`。成功结果中的 `CompiledApplication` 拥有临时目录、选定并捕获的 Java classpath、注解处理产物和执行计划；每次执行独立打开运行资源。编译器或 runner 关闭不使已交付应用失效，应用调用方负责关闭产物。Java 方法索引由注解处理阶段产生一次，运行、测试与 Native 构建复用同一结果。入口见 [application](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/main/java/dev/w0fv1/norm/application)。

`ProjectResources` 以模块归属和资源内容定义值相等，并派生 classpath 资源视图；应用缓存复用遵循完整输入的值语义，生命周期约束见 [PolyglotProjectTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/polyglot/PolyglotProjectTest.java)。打包读取捕获的资源，不重新扫描工作目录。模块归档和 JAR 通过 `FileSnapshot` 校验内容身份，复制后复验，拒绝为变化后的文件沿用旧身份。归档与发布约束见[应用构建](/tooling/application-build)。

Java 绑定的扫描与继承关系共用 `JavaTypeProjector`。`JarApiScanner` 唯一产生包含有效继承成员的完整 schema，绑定规划仅消费该 schema。`BindingPlanner` 产生不可变 `BindingPlan`，固定导出、名称、签名和调用表；`BindingSourceRenderer` 消费计划生成源码。实现入口见 [JAR 绑定生成器](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarBindingSourceGenerator.java)。

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

作者态声明身份统一由 `DeclarationIdentity` 根据文档、声明类别、名称和规范签名构成，不包含声明序号与源码偏移。成员身份从 owner 派生，类型参数身份从所属声明与参数序号派生；重载解析族按可见性作用域归组，public 声明可跨文档属于同一族，private 声明保持文档局部。

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

`CompilationResult` 直接持有 `CompilationOutput`，`ExecutionBackend` 与 Lowerer 只消费 `CoreArtifact`。Lowerer 使用已解析 Core，生成函数 `CallTarget`、frame slot、控制流节点、固定目标调用和按静态方法或 interface requirement `DefinitionId` 索引的分发表。class 与 interface 调用共用动态分派入口；遍历式 `for` 通过 `Iterable<T>` 和 `Iterator<T>` requirements 工作，内建集合返回内部 `NativeIterator<T>` 运行时值。

`ExecutionContext` 作为隐藏根参数沿固定调用边传递，可执行节点不捕获单次运行状态。standalone `TruffleExecutionBackend` 以 `ExecutableId` 在有界缓存中保存上下文无关的可执行程序，空白、位置和源码 URI 变化只改变 `DebugInfoId`；运行错误通过当前 artifact 的 authoring sidecar 映射位置。需要 Truffle source instrumentation 的 Polyglot 路径把 `DebugInfoId` 纳入实例化边界。内建 ABI 指纹属于后端 ABI key。

guest 运行错误在 Truffle 节点处携带稳定错误码和 `SourceSection`，跨公开边界后转换为结构化 `NormExecutionException`。自包含 CLI 打包同一 Core、Truffle 执行链与平台 runtime。

## 验证

[DependencyArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/DependencyArchitectureTest.java) 是可执行的包边界与无环依赖约束。

[NativeApplicationArchiveTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/runtime/NativeApplicationArchiveTest.java) 在 classpath 与发行模块路径下验证原生应用归档的写入、读取和执行。

身份测试覆盖源码移动、泛型 alpha rename、显式与推断类型实参、模块版本、名义类型、声明重排、递归 SCC、类型依赖传播和 authoring occurrence 路由。存储测试覆盖准入策略、只读校验、损坏恢复、并发发布、并发清理与跨实例读取。边界测试覆盖 Core 类型与操作 ABI、namespace shape 和重复 group；后端测试覆盖 Core-only 依赖、DefinitionId 枚举身份、artifact 复用、独立执行上下文、Polyglot 入口、源码位置和 guest 调用栈。
