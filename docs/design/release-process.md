# 发布流程

Norm 使用符合语义化版本的 Git tag 触发发布。tag 中的 SemVer 是 CLI、语言服务器、VS Code 插件、文件名和 GitHub Release 的唯一发布版本来源。发布过的版本号不得重复使用。

## 发布物

每个版本同时发布各平台的独立 CLI，以及内置全部受支持平台 CLI 的唯一通用 VS Code 插件：

| 平台 | CLI |
| --- | --- |
| Windows x64 | ZIP 内的 `norm.exe` |
| Linux x64 | TAR.GZ 内的 `norm` |
| macOS Apple Silicon | TAR.GZ 内的 `norm` |

`norm-language-support-vMAJOR.MINOR.PATCH.vsix` 是唯一插件产物。插件根据 VS Code 所在的操作系统和架构选择内置 CLI，不发布平台专用 VSIX。

GraalVM 25 已停止提供新的 macOS Intel 构建，因此不建立依赖退役工具链的 macOS x64 发布线。新增平台必须先进入持续集成并通过相同验收。

## 验收门槛

发布必须同时通过 Java 工具链测试、VS Code 静态检查、原生 CLI 版本检查、Hello World、`norm/tests` 中的全部可执行验收程序和原生 LSP 握手。通用 VSIX 必须包含目标清单中的全部 CLI，并逐一验证与各平台已验收二进制文件完全一致。

构建完成后统一生成 SHA-256 校验和与构建来源证明。任一平台失败时不发布任何平台；全部资产先进入 Draft Release，上传完整后再一次性公开。

## 自动化

[发布目标清单](https://github.com/w0fv1/norm/blob/main/tool/cli/release-targets.json)是平台、runner、CLI 路径和插件内目录的唯一机器定义；打包器与 [Release 工作流](https://github.com/w0fv1/norm/blob/main/.github/workflows/release.yml)共同读取它。日常 CI 负责尽早验证 Native Image，Release 工作流只接受 `vMAJOR.MINOR.PATCH` tag。

公开版本应逐步接入 Windows Authenticode 签名以及 macOS Developer ID 签名和 notarization。签名接入前，版本说明必须明确系统可能显示来源警告。

## 版本说明

版本说明只记录该版本实际交付的语言能力、工具变化、迁移要求和已知限制。发布前必须存在由 tag 的 `major.minor` 派生出的中英文版本记录。当前实现边界见 [Norm 0.3 版本记录](/versions/0.3)，未来语言规范不作为当前编译器的交付承诺。
