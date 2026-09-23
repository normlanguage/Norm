# 发行版源码构建方案

本方案把 Debian、Fedora 和 Homebrew 官方收录所需的源码构建纳入上游构建架构。实施前的当前事实仍见[工具链开发规范](/design/toolchain-development)；本页规定迁移目标、边界和验收顺序。

## 准备解决的问题

1. Norm 当前锁定 Gradle 9.7.1、Kotlin DSL 和外部 Gradle 插件。Debian 与 Fedora 的隔离构建禁止在构建期间下载这些工具，而发行版仓库没有可直接执行现有构建的兼容工具链。继续打包 Gradle 及其 Kotlin 闭包会扩大前置审核范围，也不会改善 Norm 自身的可维护性。
2. 编译、代码生成、运行时组装、Native Image metadata、launcher 和发行归档集中在 `build.gradle.kts`。这些行为缺少独立的强类型边界，难以同时供开发构建、发布工作流和发行版构建复用。
3. Kryo 5 及其 MinLog、ReflectASM、Objenesis 依赖扩大了 Debian/Fedora 的前置包集合。`PortableObjectCodec` 还同时承载缓存、内容身份和可分发程序数据；隐式对象图序列化不适合作为长期制品格式。
4. 现有发布资产已经由 `release-targets.json`、发布模型和渠道清单生成器统一定义。构建迁移必须保留这些单一真相源，不能为发行版另写一套产品构建。

## 目标

- Maven Reactor 成为唯一上游构建入口，根 POM 成为 Java、插件和依赖版本的唯一声明源。
- 保留一个产品 JPMS 模块 `compiler`；新增的 `build-tools` 只包含构建期程序，不进入 Norm 运行时或公开 API。
- 普通开发、GitHub Release、Debian、Fedora 与 Homebrew 使用同一编译、测试、代码生成和组装实现。各渠道只选择依赖来源及所需交付物。
- Debian 使用发行版 Maven 仓库与 `maven-debian-helper`，Fedora 使用 Java Packaging Tools 与系统 Maven artifact；两者在断网的干净构建根中从源码完成构建。
- 在 Maven 迁移稳定后，以显式、版本化、确定性的编解码替代 Kryo，并删除不再使用的 Kryo 依赖链。
- 保持 CLI、LSP、应用构建、Native Image、发行资产名称和渠道清单的对外行为不变。

## 非目标

- 不长期维护 Gradle 与 Maven 两套构建。
- 不把预编译 Gradle、Kotlin、JAR 或发行二进制作为 Debian/Fedora 源码构建输入。
- 不为发行版新增手写 `javac` 构建或另一套代码生成脚本。
- 不在构建迁移中改变语言语义、CLI 命令、模块格式或发布目标清单。
- 不在同一个变更中迁移构建系统和持久化格式。

## 目标架构

```text
pom.xml
├── build-tools/             构建期 Java 程序
├── build-maven-plugin/      Maven 解析模型的薄适配器
└── cli/compiler/            唯一产品与 JPMS 模块
    ├── compile / test
    ├── generated sources
    └── distribution assembly

release-targets.json         平台与目录的机器定义
release-model.mjs            版本与资产名称
distribution-manifests.mjs   渠道清单
```

`build-tools` 使用普通 Java 类型实现现有 Gradle 自定义任务的行为：构建元数据、Builtin ABI、工具链 artifact 清单、reachability metadata、运行时 launcher、`jlink` runtime、native launcher 和发行目录。无需 Maven 项目模型的任务由发行版已有的 `exec-maven-plugin` 接入；依赖图与真实 JAR 文件由 `build-maven-plugin` 从 Maven 解析模型取得，再调用同一组装器和清单生成器。行为测试直接调用 Java API；POM 只负责编排顺序和输入输出路径，不承载领域逻辑。

根 POM 统一声明 Java 25、依赖、测试和插件版本。发布工作流从 SemVer tag 派生 Maven `revision`，tag 仍是发布版本的唯一来源。`release-targets.json` 继续定义平台、runner、目录与 launcher；Maven 和脚本只读取它，不复制目标矩阵。

构建只保留以下生命周期差异：

| 入口 | 产物 | 依赖来源 |
| --- | --- | --- |
| 默认 `verify` | 编译器与测试 | Maven 仓库或本地缓存 |
| `release` | 自包含 runtime、launcher、VSIX 输入与发布归档 | 锁定的上游依赖与 release metadata |
| 发行版 `package` | 使用系统 JDK 和系统 Java 库的 CLI 安装树 | Debian/Fedora 系统 Maven 仓库 |

三个入口复用相同的编译器模块、生成器和测试。发行版入口不执行跨平台发布任务，也不引入不同的源码、条件编译或产品实现。

## Kryo 替换边界

构建迁移先原样携带 `PortableObjectCodec`，确保差异只来自构建系统。Maven 成为唯一入口后，再按数据所有权拆分显式 codec：

- 可删除缓存使用带 schema 版本的内部格式，版本不匹配时由所有者删除并重建；
- 内容寻址数据使用规范字段顺序、稳定集合顺序和固定整数编码，摘要只基于规范字节；
- NAR、published binding 和 application program 等可分发数据使用带 magic、格式版本和长度边界的公开二进制 envelope；
- 每种格式由所属领域拥有，`core.store` 只保留共享的二进制读写原语，不保留通用反射对象序列化器。

迁移采用 golden bytes、往返、确定性、损坏输入和大小边界测试。全部调用方切换并验证后，一次性删除 `PortableObjectCodec`、Kryo、MinLog、ReflectASM 以及经依赖分析确认不再使用的 Objenesis。

## 实施阶段

### 0. 发行版可行性基线

首轮目标为 Debian sid/main 与 Fedora Rawhide。先记录目标仓库、架构及快照，再从真实解析图调查运行时、代码生成、annotation processor、测试、构建插件及其依赖、父 POM/BOM 与非 Maven 输入的供应。依赖版本仍由现有构建声明；报告不另设手写版本清单。

已安装运行时候选证据由 [发行版依赖预检](../../cli/compiler/scripts/distribution-preflight.md) 采集。它不查询仓库可用性，不覆盖完整构建闭包，不证明 API/JPMS 兼容或源码构建成功。未安装、仓库未查询和查询失败分别记录。

用系统依赖先打通构建工具、必要代码生成、单一 JPMS 编译器、系统安装树及真实 CLI/LSP 验收。明确系统库独立升级后的工具链身份、缓存失效和 Native Image 供给方式；安装与基础验收不能隐式下载工具链。缺包时先确定依赖升级、源码打包或上游消除依赖的处理路径。

完成条件：每个已知构建输入有系统来源或明确缺口；现有模块适配与合并 JAR 的系统库方案得到验证。该阶段不以找到同名系统包或成功生成清单代替真实运行。

### 1. 固定等价性契约

先把现有 Gradle 任务的输入、输出、生成文件、发行目录和调用脚本形成可执行验收。覆盖编译、Builtin ABI、版本元数据、工具链清单、CLI、LSP、应用归档、`jlink` runtime、Native Image 输入和发布资产命名。

完成条件：当前 Gradle 构建通过这些定向验收，且每项产物都有唯一所有者和比较规则。

### 2. 建立 Maven 编译与测试

添加根 Reactor、`build-tools` 和 `compiler` POM，先迁移 Java 编译、JPMS、Truffle annotation processor、JUnit 与架构测试。此阶段 Gradle仍是发布入口，Maven 仅用于等价性验证。

完成条件：同一提交上，两套入口编译相同源码并通过选定的编译器、CLI 和 Truffle 测试；依赖图差异得到解释。

### 3. 迁移生成与组装

按依赖顺序把 Gradle 自定义任务迁入 `build-tools`，先迁移生成源码，再迁移工具链清单与运行时组装，最后迁移 native launcher 和发布归档。每迁移一项先写行为测试，再切换 Maven 生命周期。

完成条件：Maven 产物通过阶段 1 的全部比较，发布脚本不读取 Gradle 输出。

### 4. 切换唯一构建入口

将 CI、Release、开发命令和文档切换到 Maven。完成完整发布验收后删除 Gradle wrapper、Kotlin DSL、版本目录和仅服务 Gradle 的脚本；同时更新实现策略与工具链开发规范。

完成条件：仓库不存在活动 Gradle 构建入口，干净克隆只依赖 JDK、Maven、Node 和各目标平台发布工具即可完成对应流程。

### 5. 验证发行版源码包

Debian 包使用 `maven-debian-helper` 和 `/usr/share/maven-repo`；Fedora spec 使用 Java Packaging Tools 的 Maven dependency mapping。先生成全部 Build-Depends/BuildRequires，再在断网的 `sbuild` 与 `mock` 中构建和测试。

完成条件：Debian `sbuild`、`lintian`、安装后 `autopkgtest`，以及 Fedora Rawhide `mock`、`rpmlint`、安装后 CLI/LSP 测试全部通过；日志证明构建期间没有网络访问或预编译上游二进制。

### 6. 替换 Kryo

按缓存、内容寻址和可分发格式的顺序引入显式 codec。一个所有权域完成读写切换后删除对应 Kryo 注册逻辑，最终删除整个依赖链。

完成条件：所有格式测试、CLI、LSP、增量缓存、NAR、动态 Java binding、应用构建和 Native Image 验收通过；生产源码与依赖清单不再引用 Kryo。

### 7. 恢复发布主线

使用已经验证的源码构建继续 Debian `normlang`、Fedora `normlang` Package Review 和 Homebrew core 技术准备；Snap、winget、自有 Homebrew Tap 与 Scoop Bucket 继续复用 GitHub Release 资产。

完成条件：各渠道状态与真实提交、审核或收录结果一致，不把等待审核表述为完成。

## 验收门槛

- Maven 在线开发构建、预填充本地仓库后的 `--offline` 构建和发行版系统仓库构建均通过。
- Java 25、JPMS、annotation processor、JUnit、ArchUnit 和 Spotless 等现有约束没有静默降级。
- CLI 版本、hello 程序、诊断、动态 Java binding、LSP 握手、应用归档、一次 Native Image 构建和最终发行目录通过真实执行。
- 相同源码、版本和输入产生相同的规范字节、内容摘要、清单与资产名称；包含时间戳的容器格式先规范化再比较。
- Release workflow 仍只接受 SemVer tag，并从现有发布目标清单生成完整资产与渠道清单。
- Debian 与 Fedora 的构建日志能够列出所有系统构建依赖，且网络隔离不会改变结果。

## 主要风险与控制

| 风险 | 控制 |
| --- | --- |
| 自定义 Gradle 任务的隐含顺序丢失 | 阶段 1 固定输入输出；`build-tools` API 表达依赖，Maven 只编排生命周期 |
| annotation processor、JPMS 或测试选择不同 | 在阶段 2 比较编译参数、模块描述符、生成类与测试清单 |
| 发行版缺少某个 Maven 插件 | 只选择 Debian/Fedora 已打包的插件；复杂行为放入本仓库 Java 工具，不继续增加构建插件 |
| 双构建长期并存 | Maven 达到发布等价后立即完成阶段 4，删除 Gradle；不在双构建状态开发新构建能力 |
| Kryo 替换破坏缓存或公开制品 | 与构建迁移分开；按数据所有权定义版本、兼容策略和 golden bytes |
| 发行版依赖版本与上游锁定版本不同 | 先运行 API/行为兼容测试；必要升级在上游完成，发行版 spec 不携带产品逻辑补丁 |

## 决议

选择 Maven 作为唯一构建系统。Debian 官方文档推荐 Maven 项目使用 `maven-debian-helper`，并明确说明 Maven 相较 Gradle 在 Debian Java 打包中支持更成熟；Fedora 的现有依赖构建证据也已经验证 Maven compiler、Surefire 与 exec plugin 可以由系统包提供。迁移的长期收益是删除 Gradle/Kotlin 构建闭包、把构建逻辑变成可测试 Java 组件，并让上游与发行版共享一条真实构建链。
