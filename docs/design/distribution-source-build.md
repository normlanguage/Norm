# 发行版源码构建架构

Norm 以根 [Maven Reactor](../../pom.xml) 作为开发、CI、Release 与发行版源码包的唯一上游构建入口。它包含构建期的 [`build-tools`](../../build-tools/)、薄适配器 [`build-maven-plugin`](../../build-maven-plugin/) 和唯一的产品 JPMS 模块 [`compiler`](../../cli/compiler/pom.xml)。构建期模块不进入 Norm 运行时或公开 API。

## 构建边界

`build-tools` 用强类型 Java 组件实现构建元数据、Builtin ABI、依赖清单、reachability metadata、launcher、运行树、`jlink` runtime 与发布归档。`build-maven-plugin` 只把 Maven 实际解析的依赖图和 JAR 文件交给这些组件；POM 编排生命周期，不复制产品逻辑。行为边界直接由各组件测试验证。具体入口见[工具链开发规范](/design/toolchain-development)。

[发布目标清单](../../cli/compiler/release-targets.json)唯一规定平台、runner、发行目录和 launcher；[发布模型](../../cli/compiler/scripts/release-model.mjs)派生版本与资产名，[渠道清单生成器](../../cli/compiler/scripts/distribution-manifests.mjs)派生包管理器清单。发布版本来自 SemVer tag，Maven `revision` 由它传入，不建立第二份版本或平台矩阵。

| 入口 | 交付范围 | 依赖来源 |
| --- | --- | --- |
| `./mvnw verify` | 编译、测试与 Java 格式检查 | 锁定的上游 Maven 依赖 |
| `./mvnw -Prelease package` | 自包含运行树与目标平台发布资产 | 锁定的上游 Maven 依赖及已校验的非 Maven 输入 |
| 系统 Maven `package` | 使用系统 JDK 和系统 Java 库的发行版安装树 | Debian/Fedora 系统 Maven 仓库 |

发行版构建调用系统 Maven，不执行 wrapper，也不依赖 wrapper 下载的 Maven。离线的本地 Maven 缓存构建、使用发行版系统依赖的构建、以及从干净 SRPM 或 Debian 源码包重建是三个独立门槛。缺少 reachability metadata 等非 Maven 输入时，构建须明确报缺；可用 `-Dnorm.reachability.archive=<本地归档路径>` 提供已校验归档，不能在断网构建中隐式下载。输入契约见 [`ReachabilityMetadataArchive`](../../build-tools/src/main/java/dev/w0fv1/norm/packaging/ReachabilityMetadataArchive.java)。

Debian 使用 `maven-debian-helper` 与 `/usr/share/maven-repo`，Fedora 使用 Java Packaging Tools 的系统 artifact 映射。包配方声明完整 Build-Depends 或 BuildRequires，并在全新隔离环境中断网重建；不得把预编译的编译器、JAR、Gradle 或 Maven 缓存作为源码包输入。本地候选依赖仓库可证明包链技术可重建，正式收录仍须目标官方仓库提供所需依赖。

## 发布与源码包验收

- 上游同一源码通过 Java 25、JPMS、annotation processor、测试与格式检查；安装后的父子 POM 可在源码树外独立解析。
- 目标平台资产由同一构建模型生成，版本、文件名、运行树、许可、工具链清单及摘要一致；CLI、LSP、动态 Java binding、应用归档与 Native Image 经真实安装后运行验收。
- 随包 JDK 通过 `jlink` 生成；系统 JDK 安装树与便携安装树复用同一产品实现。完整平台与 VSIX 交付门槛见[发布流程](/design/release-process)。
- Debian sid 的干净 `sbuild`、`lintian`、`autopkgtest` 与安装后验收，以及 Fedora Rawhide 的干净 `mock`、`rpmlint`、安装后验收分别通过。日志记录依赖仓库、构建根和断网条件；源码包构建成功不等于已经入库。

## Kryo 替换边界

Kryo 格式替换独立于构建入口与发行版配方，由各数据所有者提供显式编解码：

- 可删除缓存使用带 schema 版本的内部格式，版本不匹配时由所有者重建。
- 内容寻址数据固定字段、集合顺序与整数编码，摘要只基于规范字节。
- NAR、published binding 和 application program 等可分发数据使用带 magic、格式版本和长度边界的公开二进制 envelope。

每种格式须通过 golden bytes、往返、确定性、损坏输入和大小边界测试。全部调用方切换后，删除 `PortableObjectCodec`、Kryo 及经实际依赖分析确认不再使用的 MinLog、ReflectASM、Objenesis；`core.store` 只保留共享的二进制读写原语。
