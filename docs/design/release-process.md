# 发布流程

Norm 使用符合语义化版本的 Git tag 触发发布。tag 中的 SemVer 是 CLI、语言服务器、VS Code 插件、文件名和 GitHub Release 的唯一发布版本来源。发布过的版本号不得重复使用。

## 发布物

每个版本同时发布各平台的自包含 CLI，以及内置全部受支持平台 CLI 的唯一通用 VS Code 插件：

| 平台 | CLI |
| --- | --- |
| Windows x64 | 可直接执行和自安装的 `norm.exe` |
| Linux x64 | TAR.GZ 内的 `norm/bin/norm` |
| macOS Apple Silicon | TAR.GZ 内的 `norm/bin/norm` |

各平台使用同一个由 `bin`、编译器 `lib` 和 JDK 25 `jlink` `runtime` 组成的运行时。Windows 的 `norm.exe` 原样内嵌该目录，首次运行时按版本原子展开；`norm.exe setup` 将 EXE 安装到当前用户、幂等写入用户 `PATH`，并准备固定版本的 GraalVM Community Native Image 工具链。Native 工具链不重复塞入 CLI 与通用 VSIX，而是按平台下载到 `~/.norm/toolchains/native-image`，验证官方 SHA-256 后原子安装并复用。未先执行 setup 时，首次 native build 使用同一安装流程。

`norm-language-support-vMAJOR.MINOR.PATCH.vsix` 是唯一插件产物。插件根据 VS Code 所在的操作系统和架构选择内置的同结构 CLI，不发布平台专用 VSIX。

新增平台必须先进入持续集成并通过相同验收。

## 验收门槛

发布先构建各平台 CLI 并打包通用 VSIX，再集中执行工具链测试和最终交付验收。语言程序由 `ProgramExecutionTest` 统一覆盖，不在各平台 CLI 验收中重复运行。每个平台验证版本、源码执行、动态 Java binding、一次 native 构建及三次隔离启动，以及 LSP 和编辑器集成。Windows 另外验证便携执行、setup 和 PATH 幂等。通用 VSIX 校验全部目标的内置运行时及宿主平台执行。

框架、ORM 和应用验收归各适配包与 [examples 仓库](https://github.com/normlanguage/examples)所有，不作为编译器发行任务。

构建完成后统一生成 SHA-256 校验和与构建来源证明。任一平台失败时不发布任何平台；全部资产先进入 Draft Release，上传完整后再一次性公开。

## 自动化

[CLI 验收入口](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/verify-cli.mjs)只覆盖工具链与通用 Java 互操作。

[发布目标清单](https://github.com/normlanguage/Norm/blob/main/cli/compiler/release-targets.json)是平台、runner、发行目录、launcher 和插件内目录的唯一机器定义；打包器与 [Release 工作流](https://github.com/normlanguage/Norm/blob/main/.github/workflows/release.yml)共同读取它。日常 CI 验证工具链；Native size 工作流提供独立手动体积门禁，Release 工作流只接受 `vMAJOR.MINOR.PATCH` tag。

公开版本应逐步接入 Windows Authenticode 签名以及 macOS Developer ID 签名和 notarization。签名接入前，版本说明必须明确系统可能显示来源警告。

## 版本说明

版本说明只记录该版本实际交付的语言能力、工具变化、迁移要求和已知限制。发布前必须存在由 tag 的 `major.minor` 派生出的中英文版本记录。当前实现边界由[版本索引](/versions/)指向的最新实现契约定义，未来语言规范不作为当前编译器的交付承诺。
