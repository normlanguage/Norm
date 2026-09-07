---
title: 应用构建
description: 将 Norm 应用构建为自包含可执行文件
---

# 应用构建

`norm build` 默认使用 GraalVM Native Image，把应用、确定版本的 NAR、Java 制品和所需运行时代码编译为本机可执行文件。构建时完成依赖解析、类型检查和闭世界链接；生成的程序直接启动，运行时不解压 JVM、不访问 GitHub、Maven 或 Gradle，也不要求目标机器安装 Norm 或 Java。

首次运行 `norm setup` 会把 Norm Native Image 工具链安装到 `~/.norm/toolchains/native-image`。如果跳过 setup，第一次 `norm build` 也会自动安装；下载内容使用 Norm 固定的官方 GraalVM Community 版本，并在解压前验证 SHA-256。后续构建复用同一工具链。

## 单文件

在源文件所在目录执行：

```text
norm build web.norm
```

Windows 产物是同目录的 `web.norm.exe`；Linux 与 macOS 产物是同目录的 `web`。

Windows 单文件构建只在源码旁生成 EXE，不生成 `.norm`、`build` 或散落的 DLL。需要附属库时，EXE 内嵌完整原生运行制品；首次启动校验并解包至 `~/.norm/cache/native-applications/<内容哈希>`，后续启动复用。启动器不携带 JVM 或 Norm 编译器，不改变工作目录、程序参数和退出码。该形态是单文件交付，不等同于完全静态链接；报告中的 EXE 大小与原生镜像大小分别统计。

Linux 与 macOS 仍按 GraalVM 制品清单交付必需附属库。构建报告列出实际运行文件、哈希及总大小，部署时按清单一起复制。

依赖和工具链使用用户级缓存；注解处理和原生构建的中间文件归构建会话所有，结束或失败后清理。程序主动创建的数据库、日志等业务数据不属于构建临时文件，不会自动删除。

`std.application.applicationDirectory()` 返回类型化的 `std.filesystem.Path`：打包运行时指向对外 EXE 所在目录，源码运行时指向入口源码目录。它不受内部解包缓存位置或启动命令的当前工作目录影响；显式相对文件路径仍按当前工作目录解释。

## 项目

项目以根 package 中的 `module.norm` 和 `application.norm` 为入口。在项目目录执行：

```text
norm build
```

也可以显式选择项目目录：

```text
norm build .
```

产物写入 `<module>/build/<artifact>[.exe]`；`artifact` 使用 Module 仓库坐标映射，例如 `hello.web` 在 Windows 生成 `build/web.exe`。

## 构建目标

构建日志默认显示目标、依赖解析、NAR 缓存路径、编译、注解处理和打包阶段，以及累计耗时。仓库请求失败时保留请求地址和底层异常链；`Built` 仅在产物写入成功后打印。

默认目标是 `native`。如需诊断 JVM 行为，可显式执行：

```text
norm build --jvm web.norm
```

`--jvm` 是开发与兼容目标，不改变默认发布语义。Native Image 的第一次完整框架构建可能需要数分钟；这是构建期成本，生成程序的启动不再承担 JVM、依赖解析或解包成本。

Native Image 还需要操作系统 C 工具链：Windows 使用带 Windows SDK 的 Visual Studio 2022 C++ Build Tools，Linux 使用 GCC 和系统开发库，macOS 使用 Xcode Command Line Tools。Norm 管理 GraalVM，不隐式修改这些系统级开发工具。

支持的平台与校验规则见[发布流程](/design/release-process)。

## 体积报告

原生构建自动裁剪不可达的 Core 定义和 Java 调用，运行时链接与反射配置使用同一份保留集合。不需要用户编写裁剪白名单。框架生成类型作为外部入口保留，注解随所属声明保留；动态 Java 调用名称无法静态确定时，保留全部绑定调用。Java 类型映射、框架资源和服务发现配置目前保守保留。实现与测试入口分别为 `CoreReachability`、`CoreReachabilityTest`。

Core 依赖分类和声明/方法体遍历由 `CoreWalker`、`CoreDependency`、`CoreTree` 统一提供；直接调用、虚调用与接口调用分别记录。内置操作遍历覆盖表达式、集合物化、索引读写、迭代和接口实现；声明中的内建实现不等同于已执行。边界验证见 `CoreDependencyTest`。

Core 制品保持完整定义组；`CoreExecutionPlan` 在其上选择 callable 成员与派发槽，供 Java 调用筛选和 Lowerer 共同使用，并随 Native 构建归档传入 Hosted 准备阶段。未选择的方法不生成执行节点，未请求的派发槽不进入运行表，声明签名仍可保留。普通引用类型的构造器按执行需求选择，运行时物化所需的值类型、注解与异常构造器保守保留；构造器反射枚举保留完整构造集合。类型结构和已请求槽的全部可能实现仍保留，尚不按接收者实例化进一步收紧。反射方法枚举保守激活完整派发集合，注解生命周期使用既有 Core 协议。节点与运行表选择、外部入口及缓存隔离见 `ExecutionSelectionTest`，动态 Java 调用边界见 `CoreReachabilityTest`，归档一致性见 `NativeApplicationArchiveTest`。

执行图在构建期准备，内置操作按实际调用绑定，运行时不再进行 Core 到执行节点的转换。用户代码及依赖运行环境的服务仍在启动后执行和创建。实现入口为 `NativeApplicationFeature`、`TruffleExecutionBackend.prepare`、`IntrinsicOperation`；执行上下文隔离测试见 `PreparedExecutionTest`。

HTTP、文件、字节 IO、时间及 Java 任务内置操作在各自 Dispatcher 的 `resolve` 中逐项绑定，执行时不再按操作码选择整个操作族；异常转换仍使用共享执行边界。结构约束见 `IntrinsicOperationTest`，请求、文件、时钟和资源验证见 `HttpClientTest`、`FileSystemTest`、`ByteStreamTest`、`TimeIoFoundationTest`，任务回调验证见 `JarBindingConcurrencyIntegrationTest`。其他操作族的分派边界仍以各自实现为准。

标准 Java 服务由 GraalVM 的 ServiceLoader Feature 处理，框架自定义服务由框架随制品提供的 Native Feature 处理。Norm 不再为全部服务重复注册反射构造器或全量服务资源通配符，Native 归档也不携带服务扫描表；应用明确声明的资源按具体路径保留，名称中的 `*` 不扩展为查询通配符。回调代理及应用执行桥接由 `NativeApplicationFeature` 注册。构建期处理器发现使用的 `JarServiceIndex` 独立保留，不替代运行期服务处理。

构建归档与运行入口分离；运行入口持有执行图、调用表、类型链接及应用包名，不持有整组 `LinkedJarBinding` 构建描述或服务扫描索引。调用表经 `LinkedJarBinding.linkCalls` 统一链接，重复调用标识直接拒绝；运行期类型与枚举识别仍使用完整的已链接类型表。边界与执行验证见 `NativeApplicationProgramTest`、`LinkedJarBindingTest`。异常位置与注解声明名称分别使用 `RuntimeSourceMap`、`RuntimeDeclarationIndex`，不为这两项功能持有完整源码映射；派生一致性验证见 `RuntimeSourceMapTest`。执行入口、反射和 Java 桥接共享 `RuntimeProgram` 的结构声明、函数签名及注解策略，不通过该索引保留函数体和局部变量表；投影边界见 `RuntimeProgramTest`。类型与注解仍复用 Core 数据类型，不等同于移除整个 Core 模型。

已知 Java 类型通过 `LinkedJavaClasses` 统一解析。JVM 在建立应用类加载器后解析，Native 在 Hosted 阶段解析并保留不可变结果；每次执行仍创建独立的类缓存和反向映射缓存。类型链接不触发用户类初始化，验证见 `LinkedJavaClassesTest`。动态应用类型及回调接口仍按各自契约解析，不等同于完全取消运行期类查找。

生成应用类的包级注解查询由 `NativeImageConfigurationWriter` 从类名派生，按包去重。`package-info` 不存在时保留正常的缺失语义，不生成占位类；此查询声明不开放额外成员调用。命名包、嵌套类与无名包边界见 `NativeImageConfigurationWriterTest`。

Norm 对象向 Java 物化时，分配声明由 Core 的 class/value 定义与实际生成类型的交集派生，不重新执行 Norm 构造器。接口、枚举、注解、合成容器和未生成类型不获得此声明；边界见 `NativeImageConfigurationWriterTest`，物化入口见 `JavaApplicationDispatch`。

Java 发起 Norm 构造时，`JavaApplicationDispatch` 按已链接的构造器 ID 调用，分配与初始化分离；参数转换与普通方法调用共享，不按参数数量重新选择重载。同参数数量、不同类型的构造器验证见 `JavaAnnotationBindingIntegrationTest`。

JAR 调用由 `JvmJarBindingRuntime` 将直接目标与参数/返回转换预先配对为不可变 `LinkedCalls`。JVM 在链接时准备，Native 在 Hosted 阶段准备；运行期不再按完整 Java 类型描述选择引用类型转换。Optional、Task 结果与回调双向转换递归复用相同选择入口，类查找缓存和资源状态仍属于每次执行。基础数值转换保留范围检查；验证见 `JvmJarBindingRuntimeTest`、`JarBindingConcurrencyIntegrationTest`、`NativeApplicationProgramTest`。

应用调用表由 `JavaApplicationCallLinker` 链接，经 `JavaApplicationRuntime` 提供给执行桥接；JVM 在建立应用类加载器后准备，Native 在 Hosted 阶段准备。Native 运行期不再反射创建调用注册表；框架反射需求独立保留。不可变共享、关闭隔离及无生成类型应用的边界见 `JavaApplicationCallLinkerTest`，目标初始化时机见 `JavaDirectCallBundleTest`。

框架执行入口与 Java 实例调用目标由编译类上的 `NormApplicationMethod` 经 `JavaApplicationMethodIndex.analyze` 统一派生：构造器和静态方法纳入入口，抽象方法不纳入执行入口。ApplicationRunner 的运行/测试路径与 Native 构建均将这些入口传给执行计划，经 `ExecutionBackend` 显式执行；Native 同时用于 Core 保留分析。生成类型仍保守保留，并不意味着框架成员已精确裁剪。边界与真实注解处理输出验证见 `JavaApplicationMethodIndexTest`、`JavaAnnotationBindingIntegrationTest`。

Native 构建的工具链依赖按执行与 Hosted 用途闭包选择，保留共享依赖并核对发行内容哈希；应用依赖与生成代码不受该筛选影响。选择入口见 `NativeToolchainClasspath`，用途声明见 `cli/compiler/build.gradle.kts`。Hosted 构建仍需要的依赖不等于最终 EXE 中的运行代码。

工具链物理制品图与应用图在项目编译阶段共用 `JarBindingClasspath` 的版本选择，结果由 `CompiledApplication` 持有；注解处理的编译路径、处理器路径与 Native 阶段的服务扫描、调用桥接、外部 Graal 元数据和 classpath 均从该计划派生，不在打包时再次选版本。构建期桥接加载与编译器类加载器隔离，避免父加载器中的旧版本遮蔽选定依赖。注解处理与 Native 仍使用独立进程及各自的生命周期。

清单中的 `components` 表达每个物理 JAR 承载的逻辑组件；使用其中任一组件会保留该承载制品及相关依赖。合并关系与实际 JAR 合并共用构建声明，不通过文件缺失推断组件没有代码。

保留的合并 JAR 若与应用 JAR 提供相同的主制品组件，构建会报告物理所有权冲突，不把坐标版本相同当成内容等价。规则见 `JarArtifactOwnership`；不同 classifier 的制品身份仍分别处理。

原生构建自动打印文件大小、机器码、镜像堆大小和可达方法数。默认仅在 `~/.norm/cache/build-reports/<输出路径哈希>/latest.json` 保留最近一次构建结果，中间报告随构建清理；失败结果不沿用上次成功状态。

需要完整诊断时执行 `norm build web.norm --diagnostics`。详细报告保存在同一用户级目录的独立 `run-*` 下，完整路径由构建日志提供。只有显式开启时才生成调用树、堆明细和输入指纹。CI 显式使用此选项采集证据，不依赖源码目录布局。实现入口为 `NativeBuildReport`、`TemporaryDirectory`、`NativeApplicationDelivery` 与 `cli/launcher/Norm.NativeHost`。

- `size.json`：成功构建的 EXE 路径、SHA-256 与核心体积指标；`runtimeFiles` 与 `deliveryBytes` 表示完整运行文件清单及合计字节数，`executableBytes` 仅衡量 EXE。
- `build-artifacts.json`：固定版本 GraalVM 的原始制品清单，包含运行文件和诊断文件；相对路径基于已清理的构建暂存目录。实际交付位置以 `runtimeFiles` 为准。
- `build-output.json`：Native Image 原始结构化统计，包含工具链、优化等级和分析结果。
- `dashboard.dump`：Native Image 生成的 JSON 代码与堆对象明细。
- `core-retention.json`：Core 定义组的首次保留规则、前驱组、声明名称及声明/方法体涉及的内置操作。定义链接通过 `dependency` 区分类型、构造、直接调用、虚调用、接口派发、闭包、成员声明及实现等用途，分类来自统一的 `CoreWalker`/`CoreDependency`。前驱链追溯到应用或框架入口；这是当前保守链接的归因，不是精确执行需求或全部保留路径。实现入口为 `CoreReachability.analyze`。
- `analysis/`：GraalVM 原生诊断，包含 CSV 调用关系、类初始化及替换信息，用于追查保留原因；文件名与格式由固定工具链生成。
- `native-image.args`：实际传给 Native Image 的参数，供核对 classpath 和编译选项；其中临时输入路径在构建结束后失效，不是可重放构建脚本。

- `build-inputs.json`：提交前的原始参数、有序 classpath、应用归档和 Native Image 启动文件指纹。目录按相对路径记录全部文件的 SHA-256 和字节数，包含生成类和配置；与参数文件共用唯一的参数与类路径构造入口。它不是整个 GraalVM/C 工具链或环境变量的快照，也不包含脚本启动后追加的选项；生成验证入口为 `NativeBuildReportTest`，归档格式验证为 `native-build-inputs.test.mjs`。
- `toolchain-artifacts.json`：Gradle 解析并随 Norm 发行的依赖坐标、文件名、内容哈希、根依赖、用途及选定版本的依赖边；用于追查工具链输入身份，不是应用运行依赖清单，也不代表全部进入 EXE。用途声明入口见 `cli/compiler/build.gradle.kts`。
- `java-artifacts.json`：统一 Java 链接计划选中的制品身份、路径、SHA-256 和字节数；生成前再次校验内容，发现文件与解析时的哈希不符即中止构建。它包含选定的应用和工具链 Java 制品，不表示这些制品的全部代码进入 EXE，也不包含生成的应用类与未纳入制品图的工具类目录。
- `build.log`：Native Image 阶段日志，包含 GraalVM 镜像堆分区大小，失败时也保留；不包含前面的 Norm 编译阶段。分区大小与 `dashboard.dump` 的对象合计用于区分对象数据和堆布局开销，不能将两者直接等同。
- `application-methods.json`：从应用编译类派生的 Norm 方法 ID 与 Java 声明类型、方法名、JVM 签名映射；与注解处理阶段生成的应用直接调用表共用 `JavaApplicationMethodIndex`。代理调用不扫描方法注解，框架自身反射需求不由此索引决定。
- `reachability-sources.json`：随 Norm 分发的官方元数据仓库中，实际选定的依赖版本、元数据版本、索引及源文件哈希、测试版本匹配和 `override` 声明。`versionTested` 仅表示仓库列出了该测试版本，不是应用功能保证；源目录不存在时保留该事实和空文件清单。此报告不包含 JAR 自带或处理器生成的全部元数据，也不宣称配置已在 Native 中激活。

原生验收归档入口 `cli/compiler/scripts/verify-native-size.mjs` 要求 Java 输入清单完整，拒绝重复身份、无效哈希与无效大小。归档阶段不依赖原始缓存路径仍可访问；该路径仅用于追溯构建输入，内容一致性由构建阶段核验。

Web / ORM 验收与性能测量由 [examples 仓库](https://github.com/normlanguage/examples/tree/main/scripts)维护。

`verify-native-execution.mjs` 对命令行样本验证完整交付并执行三次隔离启动，成功后写入 `execution-verification.json`。失败时将执行副本和 `execution-failure.json` 保留在归档目录，不生成成功凭证。Native size 与 Release 工作流上传 `build/reports/native-size` 作为独立的诊断制品，具体命名与保留期以工作流定义为准；这不等同于自动体积预算门禁。

已归档的功能验收报告可执行体积预算检查：

验收及比较均要求有效的 `build-inputs.json`，不为旧报告补造指纹。比较结果中 `passed` 表示既有可比应用/工具链范围下的交付体积预算；`identicalBuildInputs` 与 `buildInputChanges` 独立表示提交参数、类路径内容、归档和启动文件是否变化。类路径顺序保留，独立记录的历史根路径不参与内容比较，参数原文不作路径归一化。预算通过不等于输入相同或已证明单一改动的因果收益；归档读取只验证结构，不要求已清理的原始目录仍存在。边界见 `compare-native-size.test.mjs`。

```text
node cli/compiler/scripts/compare-native-size.mjs <baseline-report> <candidate-report> [maximum-growth-bytes]
```

整组报告使用 `compare-native-size.mjs --sets <baseline-root> <candidate-root> [maximum-growth-bytes]`。两个目录的直属子目录必须都是完整报告；按已验证的输入与功能范围匹配，不依赖随机目录名。每个候选必须对应唯一基线，样本覆盖必须完整；空集合、歧义基线、重复候选、缺失样本和不可比输入均失败。预算逐样本应用，不允许一个样本的缩小抵消另一个样本的增长。

Native size 的基线入口为 `native-size-gate.mjs`，策略与持久化分别见 `native-baseline.mjs`、`native-baseline-store.mjs`。首次缺少基线时，仅主仓库默认分支的完整验收允许生成提案；独立写入任务在测试任务成功后保存基线，状态为 `initialized`，不声称比较通过。PR 只读基线，缺少基线时明确失败，不获得仓库写权限。

基线保存在 `refs/heads/ci/native-size/<workflow-id>/<platform>`，只包含比较必需的 JSON 报告与来源记录，不保存 EXE、JAR 或大型调用图，不随 Actions 诊断附件过期。后续运行对照该固定基线，不自动滚动抬高预算；损坏证据和不可比输入不会被当成首次运行。确需重置时，在默认分支手动运行 Native size，填写 `native_baseline_reset_reason`。重置保留 Git 历史与来源，使用预期父提交保护并发写入。Actions 摘要区分待初始化、初始化、重置、通过、体积增长及证据/输入错误；Release 仍只归档证据，不运行此基线门禁。验证入口见 `native-baseline-policy.test.mjs`、`native-baseline-store.test.mjs`。

默认允许增长为零，按完整交付大小判断，超预算或证据不可比时退出非零；同时输出 EXE、代码区和镜像堆差值。比较器核对记录的工具链、优化等级、源码哈希、Java 制品内容及至少三次相同范围的功能验收。Java 输入清单验证与归档共用 `native-java-inputs.mjs`，汇总指标与 GraalVM 原始统计的一致性验证共用 `native-size-metrics.mjs`。此检查不重新运行历史程序，也不构成完整可复现构建证明：生成类、全部元数据和额外编译参数尚未形成统一输入指纹，涉及这些输入的变更仍需独立审查。

这些 JSON 是生成的分析产物，不是项目配置，也不参与程序运行。报告不会自动清理，可按需归档或删除。分析时应核对 EXE 哈希，并保持平台、工具链和优化参数一致；磁盘文件大小与链接前镜像大小并不等价，JAR 文件大小也不能当作它在 EXE 中的贡献。原始指标定义见 [Native Image 构建输出](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildOutput/)。

应用编译产物由调用方关闭，JAR 与资源捕获、方法索引及运行资源边界统一见[编译器架构](/spec/compiler-design#应用与制品边界)。实现入口为 [`ApplicationCompiler`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationCompiler.java) 和 [`ApplicationBundleWriter`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/project/ApplicationBundleWriter.java)。
