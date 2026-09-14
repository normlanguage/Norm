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
  → CompilationOutput / CompilationResultCache
  → CompilationOutput.artifact
  → ApplicationCompiler → CompiledApplication
  → Lowerer
  → Truffle CallTarget
```

## 项目与前端

`ProjectEnvironment` 先用 bootstrap 协议求值标准库的 `module.norm`，再建立共享标准库 prelude。`ProjectLoader` 递归求值 `Module module()` 返回的精确依赖图，并建立不可变 `ProjectSourceSet`。模块配置与业务程序分别编译，配置 artifact 不进入业务 Core 依赖图。项目发现与输入捕获归属 `project`；CLI 与 Polyglot 共享 `application` 的编译和资源准备，Language Server 通过 `workspace` 管理文档分析。`CompilationScope` 统一携带每个源码文档的模块名、版本、相对路径和模块直接读取边，Analyzer 与语言服务共同使用这一个可见性模型。模块规则见[模块系统](/spec/module-system)。

Lexer 与 Parser 建立语法树，语言服务的类型片段也通过 `TypeSyntaxParser` 进入同一语法实现。补全上下文直接消费 Lexer 的字面量、插值与未闭合字面量 token。`Analyzer` 组装声明、类型与函数体分析；`TypeResolver`、`DeclarationAnalyzer` 和 `DeclarationPolicyResolver` 分别拥有类型解析、声明签名和编译期策略。`SemanticModelBuilder` 是语义输出的唯一写入口；状态进入与恢复由 `TypeResolutionState`、`BodyAnalysisState` 的作用域句柄管理。试探通过 [`AnalysisJournal`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/frontend/AnalysisJournal.java) 撤销本次写入；事务边界见 [`AnalysisTransaction`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/frontend/AnalysisTransaction.java) 与 [`AnalysisTransactionTest`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/AnalysisTransactionTest.java)；组件边界见 [`FrontendBoundaryTest`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/FrontendBoundaryTest.java)。循环流状态合并仍归属 `FlowAnalyzer`。

声明阶段先于增量计划与函数体分析，冻结的声明事实见 [DeclarationAnalysis](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/DeclarationAnalysis.java)；类型解析、声明诊断与局部分析的边界由 [DeclarationAnalysisTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/DeclarationAnalysisTest.java) 验证。

[DeclarationContract](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/DeclarationContract.java) 从已解析符号及声明属性形成增量契约。实现变化与声明契约变化分别触发本地分析和依赖失效；跨会话结果、默认实现变化及签名诊断见 [DeclarationInvalidationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/DeclarationInvalidationTest.java)。Core 内容链接仍由 [CoreReusePlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CoreReusePlan.java) 判定复用。

[CoreBuildHistory](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CoreBuildHistory.java) 保存可重定位的 Core 单元与精确声明引用。实现链接变化时从这些单元重新链接，构建报告分别统计转换、链接和直接复用；相同内容的不同目标、递归组及跨进程执行见 [CoreRelinkingTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/CoreRelinkingTest.java) 与 [PersistentCompilationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PersistentCompilationTest.java)。

`CallResolver` 统一普通函数、方法、接口、构造器、内建函数与枚举构造的候选推断和选择，`CallArguments` 负责实参映射，`TypeArguments` 负责默认类型参数补全。Diamond 构造与所属类型参数共同参与候选求解；候选适用性和最终参数校验共用类型关系，行为约束见 [SemanticProbeTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/SemanticProbeTest.java)。`ResolvedCall` 保存精确目标与实例化签名，Binder、签名帮助和导航读取这份结果。分析阶段的边界由 [AnalyzerTypeArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/AnalyzerTypeArchitectureTest.java) 约束。

`TypeRelations.DeclarationGraph` 统一名义类型投影、赋值关系与共同类型求解，前端提供声明关系，`SemanticModel` 提供冻结后的关系；两者通过 `TypeApplication` 解析类型应用，泛型推断共用 `TypeConstraintSolver`。内建协议查询由 `BuiltinCatalog` 实例化 ABI 契约。语义约束见 [SemanticArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/SemanticArchitectureTest.java)。`MemberRelations` 从 override 与 witness 派生成员关系，导航、引用与重命名共用这份关系；编辑器行为约束见 [AuthoringArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/language/AuthoringArchitectureTest.java)。

`Workspace` 按项目管理分析批次，`AnalysisScheduler` 合并待处理修改并取消过期任务。项目内所有打开文档原子发布同一快照；标准库源码的身份转换与完整 overlay 由 `ProjectSession` 准备，批次约束见 [WorkspaceTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/workspace/WorkspaceTest.java)。分析提交和诊断发送均校验文档版本及批次归属。LSP `DocumentService` 只转换协议数据。状态和调度入口见 [workspace](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/main/java/dev/w0fv1/norm/workspace)。

`Binder` 将已验证语义冻结为内部 resolved representation。全局调用目标、interface requirement 与 witness、字段 owner 与 ordinal、实参到形参映射、源码求值顺序、闭包目标与捕获、运行时泛型实参和 value/identity 复制语义在这里固定；后续阶段直接使用确定目标。

`CompilationRequest.Kind` 区分应用与库编译，两者共用分析、绑定与 Core 构建。库不选择应用入口；没有声明的库也可形成 Core 制品。应用入口只在执行边界要求存在，缓存身份包含编译种类。契约与持久化验证见 [LibraryCompilationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/LibraryCompilationTest.java)。

## 应用与制品边界

源码方法通过 `Syntax.FunctionDecl.implementation` 保留实现是否存在，空方法体与纯声明具有不同的语法身份。格式化和语言服务保留纯声明的签名；实现提供者尚未解析时，执行编译报告诊断，不将其降级为空函数体。相关约束见 [MethodDeclarationCompilerTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/MethodDeclarationCompilerTest.java)。

Bound 的实现存在性与源码一致；纯声明转换为 Core `MethodSignature`，不分配可执行方法体。类泛型与方法泛型的降级契约见 [ManagedMethodLoweringTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/ManagedMethodLoweringTest.java)。

无函数体的方法在 Core 中统一表示为 `CoreDefinition.MethodSignature`，签名包含名义接收者、类型参数、参数类型和返回类型；接口、class 与 value 接收者共用签名校验。接口继承与调用仍要求签名属于对应接口。签名结构与版本入口见 [CoreDefinition](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreDefinition.java) 和 [CoreIdentityVersion](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreIdentityVersion.java)。

方法分派的 `target` 引用目标声明，实现是否存在由该声明的类型决定。class 分派可以指向托管签名，继承与覆盖仍校验接收者及泛型 ABI；执行计划只将可执行目标加入本地调用图。边界约束见 [CoreManagedDispatchTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreManagedDispatchTest.java)。

`ApplicationCompiler` 返回可关闭的 `ApplicationCompilation`。成功结果中的 `CompiledApplication` 拥有临时目录、选定并捕获的 Java classpath、注解处理产物和执行计划；每次执行独立打开运行资源。编译器或 runner 关闭不使已交付应用失效，应用调用方负责关闭产物。Java 方法索引由注解处理阶段产生一次，运行、测试与 Native 构建复用同一结果。入口见 [application](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/main/java/dev/w0fv1/norm/application)。

应用交付使用 [`ApplicationBuilder`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/build/ApplicationBuilder.java)，借用调用方的 `ApplicationRunner`，关闭本次编译产物和 staging，只返回交付位置或编译诊断。应用执行保留决策由 [`ApplicationProgramPlan`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationProgramPlan.java) 产生；原始应用与 retained artifact 各自使用匹配的执行计划。构建不重新进行项目编译或注解处理，生命周期与归档验证见 [`build` 测试](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/test/java/dev/w0fv1/norm/build)。

`ProjectResources` 以模块归属和资源内容定义值相等，并派生 classpath 资源视图；应用缓存复用遵循完整输入的值语义，生命周期约束见 [PolyglotProjectTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/polyglot/PolyglotProjectTest.java)。打包读取捕获的资源，不重新扫描工作目录。模块归档和 JAR 通过 `FileSnapshot` 校验内容身份，复制后复验，拒绝为变化后的文件沿用旧身份。归档与发布约束见[应用构建](/tooling/application-build)。

Java 绑定的扫描与继承关系共用 `JavaTypeProjector`。`JarApiScanner` 唯一产生包含有效继承成员的完整 schema，绑定规划仅消费该 schema。`BindingPlanner` 产生不可变 `BindingPlan`，固定导出、名称、签名和调用表；`BindingSourceRenderer` 消费计划生成源码。实现入口见 [JAR 绑定生成器](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarBindingSourceGenerator.java)。

Norm → Java 的唯一生成链为 [`JavaStubPlanner`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaStubPlanner.java) → `JavaStubPlan` → `JavaStubRenderer`。计划固定类型投影、签名、继承、注解与 bridge 目标；渲染器不访问 Core 或项目。`JavaAnnotationProcessorPipeline` 消费生成源码并产生一次方法索引。字节等价、不可变计划及真实注解处理分别由 [`JavaStubPlannerTest`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JavaStubPlannerTest.java) 和 [`JavaAnnotationBindingIntegrationTest`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaAnnotationBindingIntegrationTest.java) 约束。

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

声明身份统一由 `DeclarationIdentity` 根据模块坐标、模块内源码路径、声明类别、名称和规范签名构成，不包含磁盘根目录、声明序号与源码偏移。实际文档 URI 保留在诊断与源码映射中。成员身份从 owner 派生，类型参数身份从所属声明与参数序号派生；重载解析族按可见性作用域归组，public 声明可跨文档属于同一族，private 声明保持文档局部。

内置类型由稳定的 `BuiltinTypeId` 标识，用户类型由 `CoreDefinitionLink` 标识。名义类型键包含模块名、模块版本、package、类型名和可见性；private 类型额外包含模块相对源码路径。类型改名或在模块内移动 private 类型会产生新的名义身份，移动整个项目根目录不会改变身份。class/value aggregate 的类别、泛型参数、父类型、字段布局、构造入口、方法分派和 interface conformances，interface 的泛型参数、父接口与 requirements，以及 enum variant 的稳定键与 payload 类型都属于语义内容。

`CoreNamespace` 保存 authoring 名字、签名、可见性、导出状态与精确 occurrence。 参数调用策略由 [ParameterPolicy](../../cli/compiler/src/main/java/dev/w0fv1/norm/value/ParameterPolicy.java) 在语义声明与 Core 签名之间共享，默认参数标记、标签策略和回调参数名参与公开 ABI；验收见 [ParameterContractTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/ParameterContractTest.java)。`CoreAuthoringMap` 为每个 canonical definition 保存按来源稳定编号的 `DefinitionOccurrenceId`、声明 role、`CoreDefinitionOrigin` 和引用 occurrence 路由。`CoreArtifact.metadata` 保存以 occurrence 为目标的 companion metadata。Lowerer 按调用所在 occurrence 选择对应来源，因此共享同一 `DefinitionId` 的多个源码定义仍保留各自的角色、名字、位置、调用栈和注解。

## Canonical Core

`CoreBuilder` 通过 [CoreCompilationInput](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreCompilationInput.java) 统一接收待转换声明和已编译定义。源码声明从 resolved representation 转成强类型 `CoreDefinition`，复用声明保留内容组并重新绑定当前作者态信息。callable、aggregate、enum、interface、interface method 与 builtin conformance 使用同一内容定义模型；调用、构造、enum variant、interface witness、用户类型和字段 owner 都先成为 `PendingDefinitionReference`。`CoreCanonicalizer` 遍历签名、泛型 bound、interface 关系、局部类型、运行时类型、字段和可执行表达式建立完整依赖图，并对强连通分量进行规范化：分量内引用使用成员索引，分量外引用使用完整 `DefinitionId`。整个递归组由 `DefinitionGroupId` 标识，成员由 group identity 与规范成员索引标识。

Bound 到 Core 的转换由 `BoundCoreBodyConverter` 对 sealed hierarchy 进行穷尽匹配，新增节点必须同时完成转换才能通过编译；Core traversal fixture 约束 codec、walker 与 rewriter 的覆盖。

规范化 refinement 同时使用带位置的出边和入边结构，并跳过已证明属于同一 automorphism 的搜索分支。搜索预算保留为对抗性图的资源边界；component 大小、refinement、搜索、memo 和 automorphism 剪枝统一进入 `CoreBuildReport`。

默认表达式由 [DefaultArgumentDeclarations](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/DefaultArgumentDeclarations.java) 索引，Binder 在声明自身的类型与接收者上下文中生成独立 Core callable。调用方通过显式实参调用该实现；`Let` 保证接收者只求值一次。引用结果必须证明指向长期存活的存储，借入的参数引用与临时局部地址不能逃逸；验证入口为 [CoreLetTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreLetTest.java)。默认值执行及持久复用见 [DefaultArgumentExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/DefaultArgumentExecutionTest.java) 与 [DefaultArgumentReuseTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/DefaultArgumentReuseTest.java)。参数的默认实现链接、公开签名与执行链接的身份边界、持久复用后的 occurrence 重定位见 [DefaultArgumentContractTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/DefaultArgumentContractTest.java)；声明契约由 [CoreBindingShape](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreBindingShape.java) 统一映射。归档声明导入的边界见[启动性能](/design/startup-performance)。

`CoreCodec` 是 canonical bytes 的唯一编码入口。身份版本由 [CoreIdentityVersion](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreIdentityVersion.java) 定义；编码固定版本、域分隔、节点 tag、字节序、集合顺序和字符串编码，Java 对象序列化、Truffle AST 与运行期 profile 不参与语义哈希。

整体校验从 `CoreProgramVerifier` 进入，声明校验组合 `CoreCallableVerifier`、`CoreIntrinsicVerifier` 与只读 `CoreVerificationTypes`；每个 callable 独占控制流和引用状态。边界见 [`CoreVerifierBoundaryTest`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreVerifierBoundaryTest.java)。

`CoreProgram` 在内容进入存储前验证完整闭包：名义类型与泛型 bound、callable receiver 与 reified ABI、interface 继承和完整 witness、局部和运行时类型、调用与构造目标、字段和 enum 引用、内建协议与操作契约及 namespace binding 必须彼此一致。运行时类型 capture 按类型参数索引规范排序，因此执行语义相同的 descriptor 只有一种 canonical encoding。

标准库源码经过同一条 Core 管线，并使用 `module.norm` 提供的模块坐标。`CoreBuilder` 只产生 Core 制品，持久化由 [CompilationResultCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilationResultCache.java) 管理可消费的编译结果与声明历史，统一使用有界、内容校验和原子发布的 [FileArtifactCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/FileArtifactCache.java)。作者态快照不写入编译产物缓存。

## 增量边界

`CompilerSession` 按文档内容复用解析结果，并按稳定的 `CompilationUnitId` 保留前一份 `CompilationOutput`。定义依赖被编码进 identity，因此修改叶子定义会为其依赖闭包产生新 identity，而不相关定义继续复用原有 identity 和内容组。模块以根 `module.norm` 的 URI 标识编译单元，独立文件以自身 URI 标识。

声明级分析以词法结构而非绝对偏移判断变化。空白编辑和声明重排通过 token anchor 将复用贡献映射到当前源码；声明新增、删除或签名族变化只失效对应解析族及其语义 dependents，package、import 或编译作用域变化仍按文档边界失效。

持久会话保存精确编译结果与 [CompilationHistory](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilationHistory.java)。历史通过内容键引用 Core 制品，保留语义贡献、声明身份和局部变量映射；历史所指结果被回收后重新构建 Core。分析与 Core 共用 [TokenSpanMapping](../../cli/compiler/src/main/java/dev/w0fv1/norm/syntax/TokenSpanMapping.java) 更新源码位置。[CoreReusePlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CoreReusePlan.java) 按实际声明引用传播失效；内容相同的不同声明保留各自的调用关系。独立 JVM、默认参数、局部注解及损坏恢复的约束见 [PersistentCompilationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PersistentCompilationTest.java)，实际转换数量由 [CoreBuildReport](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreBuildReport.java) 提供。Binder 仍处理整个解析程序，发布包的声明导入边界见[启动性能](/design/startup-performance)。

同一编译单元的操作按历史顺序串行提交，不同编译单元可并行分析和构建。解析缓存与编译历史只在短状态锁内读写；invalidate 和 close 使用独占生命周期边界，不与在途编译交错。

发布模块通过 [CompiledModule](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompiledModule.java) 保存本模块的语义贡献与 Core 单元，消费者由 [ImportedCompilation](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/ImportedCompilation.java) 验证声明契约、模块读取关系和源码策略。导入与增量重链接复用 [CoreBuildHistory](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CoreBuildHistory.java) 的重定位表；依赖实现变化不要求重新转换调用方。目录迁移、多个发布模块、默认参数、闭包与跨会话复用见 [PublishedCoreImportTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PublishedCoreImportTest.java)。源码与位置的紧凑序列化见 [PortableObjectCodecTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/store/PortableObjectCodecTest.java)。

`CoreDependencyIndex` 提供直接依赖、反向依赖和传递 dependents；`CoreCompilationDelta` 给出新增、复用和脱离当前 source set 的定义。类型引用与可执行引用使用同一依赖传播规则。下游缓存以这些强类型 identity 作为失效边界，内容存储命中只做完整性校验和读取。

应用的 Java 依赖通过 [JarBindingClasspath](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarBindingClasspath.java) 持有内容文件集，由 [CompiledApplication](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/CompiledApplication.java) 管理释放；应用源码变化不改变依赖文件集的身份。[DirectoryArtifactCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/DirectoryArtifactCache.java) 按内容键协调生产，校验与文件生产在目录元数据锁之外执行；生产中的目录同样受进程持有保护。

## Truffle 后端

`CompilationResult` 直接持有 `CompilationOutput`，`ExecutionBackend` 与 Lowerer 只消费 `CoreArtifact`。Lowerer 使用已解析 Core，生成函数 `CallTarget`、frame slot、控制流节点、固定目标调用和按静态方法或 interface requirement `DefinitionId` 索引的分发表。class 与 interface 调用共用动态分派入口；遍历式 `for` 通过 `Iterable<T>` 和 `Iterator<T>` requirements 工作，内建集合返回内部 `NativeIterator<T>` 运行时值。

`ExecutionContext` 作为隐藏根参数沿固定调用边传递，可执行节点不捕获单次运行状态。standalone `TruffleExecutionBackend` 以 `ExecutableId` 在有界缓存中保存上下文无关的可执行程序，空白、位置和源码 URI 变化只改变 `DebugInfoId`；运行错误通过当前 artifact 的 authoring sidecar 映射位置。需要 Truffle source instrumentation 的 Polyglot 路径把 `DebugInfoId` 纳入实例化边界。内建 ABI 指纹属于后端 ABI key。

guest 运行错误在 Truffle 节点处携带稳定错误码和 `SourceSection`，跨公开边界后转换为结构化 `NormExecutionException`。自包含 CLI 打包同一 Core、Truffle 执行链与平台 runtime。

## 验证

声明引用运算符保留导航与编译绑定，其作者态引用角色由 `SemanticModelBuilder` 记录并随语义贡献重定位。重命名、源码捕获与运算符边界见 [RenamePreviewTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/language/RenamePreviewTest.java)。

[DependencyArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/DependencyArchitectureTest.java) 是可执行的包边界与无环依赖约束。

[ApplicationProgramArchiveTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/runtime/ApplicationProgramArchiveTest.java) 在 classpath 与发行模块路径下验证原生应用归档的写入、读取和执行。

身份测试覆盖源码移动、泛型 alpha rename、显式与推断类型实参、模块版本、名义类型、声明重排、递归 SCC、类型依赖传播和 authoring occurrence 路由。存储测试覆盖准入策略、只读校验、损坏恢复、并发发布、并发清理与跨实例读取。边界测试覆盖 Core 类型与操作 ABI、namespace shape 和重复 group；后端测试覆盖 Core-only 依赖、DefinitionId 枚举身份、artifact 复用、独立执行上下文、Polyglot 入口、源码位置和 guest 调用栈。
