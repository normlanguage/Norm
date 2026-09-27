# 发行版源码构建架构

Norm 以根 [Gradle Kotlin DSL](../../../build.gradle.kts) 作为开发、CI 和 Release 的唯一上游构建入口。它包含隔离的[构建逻辑](../../../gradle/build-logic/)和产品 JPMS 模块 [`compiler`](../../../cli/compiler/build.gradle.kts)。构建逻辑不进入 Norm 运行时或公开 API；Gradle 构建输出写入仓库根 `build/`。

## 构建边界

`gradle/build-logic` 用强类型 Java 组件实现构建元数据、Builtin ABI、依赖清单、reachability metadata、launcher、运行树、`jlink` runtime 与发布归档。Gradle 任务把实际解析的依赖图和 JAR 文件交给这些组件，不复制产品逻辑。行为边界由各组件测试验证，具体入口见[工具链开发规范](/zh/design/toolchain-development)。

[发布目标清单](../../../cli/compiler/release-targets.json)唯一规定平台、runner、发行目录和 launcher；[发布模型](../../../cli/compiler/scripts/release-model.mjs)派生资产名，[渠道清单生成器](../../../cli/compiler/scripts/distribution-manifests.mjs)派生包管理器清单。正式发布版本只来自 SemVer tag，由工作流传给 `-PnormVersion`；非 tag 的开发版本来自根 Gradle 定义，候选验收不公开同版资产。

| 入口 | 交付范围 | 依赖来源 |
| --- | --- | --- |
| `./gradlew qualityCheck` | 编译、测试与 Java 格式检查 | 锁定的上游依赖 |
| `./gradlew :compiler:installRuntimeDist` | `build/compiler/norm-runtime` 自包含运行树 | 私有 Java 依赖及已校验的非 Java 输入 |
| `./gradlew :compiler:packageDistribution -PnormVersion=$VERSION` | `build/distributions` 中的目标平台正式资产 | 同一已验运行树与发布清单 |

当前自有 APT、RPM 源使用正式 Release 资产与应用私有依赖。[发行版预检](../../../cli/compiler/scripts/distribution-preflight.md)记录系统依赖候选，不把其当作已完成的官方源码包构建。离线构建、使用发行版系统库，以及干净 SRPM 或 Debian 源码包重建须分别验收；这条官方收录路线现阶段保留历史证据，不主动推进。缺少 reachability metadata 等非 Java 输入时，构建必须明确报缺，不能在断网构建中隐式下载。输入契约见 [`ReachabilityMetadataArchive`](../../../gradle/build-logic/src/main/java/dev/w0fv1/norm/packaging/ReachabilityMetadataArchive.java)。

后续官方源码包路线仍须声明完整 Build-Depends 或 BuildRequires，并在全新隔离环境断网重建；不得把预编译的编译器、JAR 或 Gradle 缓存当作源码输入。本地候选依赖仓库只能证明包链技术可重建，正式收录还须目标官方仓库提供所需依赖。

## 发布与源码包验收

- 上游同一源码通过 Java 25、JPMS、annotation processor、测试与格式检查。
- 目标平台资产由同一构建模型生成，版本、文件名、运行树、许可、工具链清单及摘要一致；CLI、LSP、动态 Java binding、应用归档与 Native Image 经真实安装后运行验收。
- 随包 JDK 通过 `jlink` 生成，Java 依赖放在应用私有目录，不替换系统 Java 库。完整平台与 VSIX 交付门槛见[发布流程](/zh/design/release-process)。
- 官方源码包收录若恢复推进，须分别完成 Debian 的干净 `sbuild`、`lintian`、`autopkgtest` 和 Fedora 的干净 `mock`、`rpmlint` 及安装后验收。记录依赖仓库、构建根和断网条件；不能把现有自有源验收当作官方收录完成。

## Kryo 替换边界

Kryo 格式替换独立于构建入口与发行版配方，由各数据所有者提供显式编解码：

- 可删除缓存使用带 schema 版本的内部格式，版本不匹配时由所有者重建。
- 内容寻址数据固定字段、集合顺序与整数编码，摘要只基于规范字节。
- NAR、published binding 和 application program 等可分发数据使用带 magic、格式版本和长度边界的公开二进制 envelope。

每种格式须通过 golden bytes、往返、确定性、损坏输入和大小边界测试。全部调用方切换后，删除 `PortableObjectCodec`、Kryo 及经实际依赖分析确认不再使用的 MinLog、ReflectASM、Objenesis；`core.store` 只保留共享的二进制读写原语。
