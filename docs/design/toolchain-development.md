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
| `packages` | Norm 仓库访问、版本选择、离线缓存与完整性 |
| `lsp` | 协议转换与独占工作区会话 |
| `application` | 应用编译产物、执行准备与资源所有权 |
| `build` | 应用构建用例、Native 计划、工具链与交付 |
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
dev.w0fv1.norm.cli.component    版本组件
dev.w0fv1.norm.cli.value        CLI 公共数据
dev.w0fv1.norm.cli.utils        无状态文本工具
```

CLI 中只有 `Main` 可以终止 JVM；原生应用的进程入口为 `runtime.NativeApplicationMain`。Controller 通过返回退出码报告结果，component 不读取命令行参数。

应用构建入口为 [`ApplicationBuilder`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/build/ApplicationBuilder.java)；CLI 只解析参数、组装服务和展示进度与结果。`build` 不依赖 CLI、Workspace 或 Truffle 节点，其他下层不反向依赖 `build`。约束与验证复用 `DependencyArchitectureTest` 和 [`build` 测试](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/test/java/dev/w0fv1/norm/build)。

LSP 启动入口为 [`LanguageServerLauncher`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/lsp/LanguageServerLauncher.java)，CLI 移交工作区，会话负责关闭并返回退出码。真实协议验收见 [`verify-lsp.mjs`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/verify-lsp.mjs)。

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

## ABI 代码生成

[`BuiltinAbiGenerator`](../../build-tools/src/main/java/dev/w0fv1/norm/codegen/BuiltinAbiGenerator.java) 属于构建期 Maven 模块 `build-tools`，不进入产品模块。[根 Reactor](../../pom.xml) 包含构建工具与 compiler；当前公共发布仍由 Gradle 执行。过渡期间 Java、Gson 和 JUnit 声明的一致性由 [构建边界测试](../../cli/compiler/scripts/build-tools-boundary.test.mjs) 校验；Java 格式版本与 Maven `verify` 检查由根 POM 声明，Gradle 过渡入口读取同一版本。Gradle 的 `codegen` 源码集直接复用该模块的源码，测试复用同一测试目录和 golden 文件。声明源仍为 `stdlib-abi.json`；任务输入、输出与独立 classpath 由 [`build.gradle.kts`](../../cli/compiler/build.gradle.kts) 定义。

版本元数据由同一模块的 [`BuildMetadataGenerator`](../../build-tools/src/main/java/dev/w0fv1/norm/codegen/BuildMetadataGenerator.java) 生成；Gradle 只提供版本、GraalVM 版本和输出目录。

工具链依赖清单由 [`ToolchainArtifactCatalogGenerator`](../../build-tools/src/main/java/dev/w0fv1/norm/packaging/ToolchainArtifactCatalogGenerator.java) 生成并校验；构建入口负责提供实际解析出的依赖图和 JAR 文件。Gradle 的入口为 [`generateToolchainArtifacts`](../../cli/compiler/build.gradle.kts)。

Maven 的薄适配器位于 [`build-maven-plugin`](../../build-maven-plugin/)：从 Maven 的解析模型取得依赖图和文件，调用同一组装器与清单生成器；它不进入产品运行时。`build-tools` 和适配器以 Java 17 字节码编译，compiler 仍以 Java 25 编译。

生成字节与指纹的约束见 [`BuiltinAbiGeneratorTest`](https://github.com/normlanguage/Norm/blob/main/build-tools/src/test/java/dev/w0fv1/norm/codegen/BuiltinAbiGeneratorTest.java)。干净构建、输入变化、重建及发行隔离由 [`verify-codegen.mjs`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/verify-codegen.mjs) 验证；不在用户工作区内修改 schema。

## 测试

- 先写或迁移失败测试，再修改实现。
- 单元测试与被测 package 对齐，内部组件不因测试而扩大可见性。
- 语法或执行变更必须覆盖诊断测试，以及 `norm/tests` 中的单文件和模块程序。
- Java 修改先运行相关 package 测试；提交前执行格式检查。发布前才运行完整发布验证。
- 后端变更必须通过 Polyglot 注册入口和 CLI 的真实 `.norm` 文件执行测试。

验收测试的领域、目录、命名、发现入口与运行命令统一由 [`norm/tests/README.md`](https://github.com/normlanguage/Norm/blob/main/norm/tests/README.md) 定义。

## 文档同步

语言行为修改语言规范；实现结构修改本规范；技术栈决策修改实现策略决议。其他页面只链接这些入口，不复制规则。

项目加载门面为 `project.ProjectLoader`；源码装载、依赖图、归档缓存及绑定准备分别由 `ProjectModuleSources`、`ProjectDependencyGraph`、`ArchivedModuleLoader` 和 `JarBindingPreparer` 承担。捕获输入、失败重试和边界契约见 [ProjectLoadingBoundaryTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/ProjectLoadingBoundaryTest.java)。

## 本地验收与测量入口

Windows 本地 CLI 与扩展使用根 Reactor 的 Maven `package`；便携工具链使用 `-Prelease package`。其安装树路径与编译器摘要由 [resolve-toolchain.ps1](../../cli/compiler/scripts/resolve-toolchain.ps1) 核验；默认版本取自[根 POM](../../pom.xml) 的 `revision`，实际构建版本取 Maven 产物元数据。CLI、扩展与 GUI 验收应记录实际产物身份，不只比较版本号。

离线构建通过 Maven 的 `-Dnorm.reachability.archive=<本地归档路径>` 或过渡期 Gradle 的 `-PnormReachabilityMetadata=<本地归档路径>` 提供 reachability metadata。归档来源与校验值只在 [ReachabilityMetadataArchive](../../build-tools/src/main/java/dev/w0fv1/norm/packaging/ReachabilityMetadataArchive.java) 声明；Gradle 定向验收见 [`verify-reachability-metadata.mjs`](../../cli/compiler/scripts/verify-reachability-metadata.mjs)。其他构建工具、插件与 Java 依赖仍须预先供应。

Maven `package` 的系统 JDK 安装树位于 `cli/compiler/target/norm-runtime`；`lib` 由 [`RuntimeModuleAssembler`](../../build-tools/src/main/java/dev/w0fv1/norm/packaging/RuntimeModuleAssembler.java) 组装，`bin` 由 [`RuntimeLauncherGenerator`](../../build-tools/src/main/java/dev/w0fv1/norm/packaging/RuntimeLauncherGenerator.java) 生成。`mvn -Prelease package` 在同一安装树中使用 [`RuntimeImageGenerator`](../../build-tools/src/main/java/dev/w0fv1/norm/packaging/RuntimeImageGenerator.java) 生成随包 JDK，并让 launcher 指向该 runtime；Gradle 的便携分发入口仍为 `:compiler:installRuntimeDist`，复用相同的生成器。当前 Maven 与 Gradle 安装树均包含解析出的 Java 依赖，不能直接视为使用发行版系统库的 Debian/RPM 包。

网络受限环境可向定向 Gradle 测试传入 `-PnormTestMavenRepository=<repository>`。测试将其中真实的 POM/JAR 复制到各自隔离的缓存，仍执行依赖解析、绑定、归档及运行验证；不设置该参数时保持远程解析。这不是干净网络或正式发布验收。输入声明见 `:compiler:test`；夹具装载见 [MavenTestRepository](../../cli/compiler/src/test/java/dev/w0fv1/norm/testing/MavenTestRepository.java)。

[compare-compiler.ps1](../../cli/compiler/scripts/compare-compiler.ps1) 用相同 Java、参数和源码交替运行两份完整依赖目录，保存编译、增量分析、执行耗时、主线程分配和观测峰值工作集。指标定义与预热次数见 [CompilerBenchmark](../../cli/compiler/src/test/java/dev/w0fv1/norm/testing/CompilerBenchmark.java)。主线程分配不是进程总分配，峰值工作集包含启动与预热；样例结果不能直接推广为工具链整体性能提升。
