# 发布流程

Norm 使用符合语义化版本的 Git tag 触发发布。tag 中的 SemVer 是 CLI、语言服务器、VS Code 插件、文件名和 GitHub Release 的唯一发布版本来源。发布过的版本号不得重复使用。

## 发布物

每个版本同时发布各平台的自包含 CLI，以及内置全部受支持平台 CLI 的唯一通用 VS Code 插件：

| 平台 | CLI |
| --- | --- |
| Windows x64 | 可直接执行和自安装的 `norm.exe` |
| Linux x64 | TAR.GZ 内的 `norm/bin/norm` |
| macOS Apple Silicon | TAR.GZ 内的 `norm/bin/norm` |

各平台使用同一个由 `bin`、编译器 `lib` 和 JDK 25 `jlink` `runtime` 组成的运行时。Windows 的 `norm.exe` 原样内嵌该目录，首次运行时按版本原子展开；`norm.exe setup` 将 EXE 安装到当前用户并幂等写入用户 `PATH`。这既不要求用户安装 Java，也保留运行时加载第三方 Java binding 和 Annotation Processor 的能力。

`norm-language-support-vMAJOR.MINOR.PATCH.vsix` 是唯一插件产物。插件根据 VS Code 所在的操作系统和架构选择内置的同结构 CLI，不发布平台专用 VSIX。

新增平台必须先进入持续集成并通过相同验收。

## 验收门槛

发布必须同时通过 Java 工具链测试、Windows 启动器测试、VS Code 静态检查、CLI 版本检查、Hello World、`norm/tests` 中的全部可执行验收程序、动态 Java binding 程序和 LSP 握手。Windows 还必须验证便携执行、安装、PATH 幂等和安装后执行。通用 VSIX 必须包含目标清单中的全部 CLI，并验证 launcher、compiler 和 runtime 来自对应平台已验收的发行目录；宿主平台的完整内置目录必须能从 VSIX 解出并执行。

构建完成后统一生成 SHA-256 校验和与构建来源证明。任一平台失败时不发布任何平台；全部资产先进入 Draft Release，上传完整后再一次性公开。

## 自动化

[发布目标清单](https://github.com/normlanguage/Norm/blob/main/cli/compiler/release-targets.json)是平台、runner、发行目录、launcher 和插件内目录的唯一机器定义；打包器与 [Release 工作流](https://github.com/normlanguage/Norm/blob/main/.github/workflows/release.yml)共同读取它。日常 CI 负责验证自包含 CLI 与动态 Java binding，Release 工作流只接受 `vMAJOR.MINOR.PATCH` tag。

公开版本应逐步接入 Windows Authenticode 签名以及 macOS Developer ID 签名和 notarization。签名接入前，版本说明必须明确系统可能显示来源警告。

## 版本说明

版本说明只记录该版本实际交付的语言能力、工具变化、迁移要求和已知限制。发布前必须存在由 tag 的 `major.minor` 派生出的中英文版本记录。当前实现边界由[版本索引](/versions/)指向的最新实现契约定义，未来语言规范不作为当前编译器的交付承诺。
