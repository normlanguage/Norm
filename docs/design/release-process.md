# 发布流程

Norm 使用符合语义化版本的 Git tag 触发发布。tag 中的 SemVer 是 CLI、语言服务器、VS Code 插件、文件名和 GitHub Release 的唯一发布版本来源。发布过的版本号不得重复使用。

## 发布物

每个版本同时发布独立 CLI 和内置同版本 CLI 的 VS Code 插件：

| 平台 | CLI | VS Code 插件 |
| --- | --- | --- |
| Windows x64 | ZIP 内的 `norm.exe` | `win32-x64` VSIX |
| Linux x64 | TAR.GZ 内的 `norm` | `linux-x64` VSIX |
| macOS Apple Silicon | TAR.GZ 内的 `norm` | `darwin-arm64` VSIX |

GraalVM 25 已停止提供新的 macOS Intel 构建，因此不建立依赖退役工具链的 macOS x64 发布线。新增平台必须先进入持续集成并通过相同验收。

## 验收门槛

发布必须同时通过 Java 工具链测试、VS Code 静态检查、原生 CLI 版本检查、Hello World、65 个单文件验收程序和原生 LSP 握手。VSIX 打包前再次执行内置 CLI 的版本检查，禁止插件和语言服务器版本分叉。

构建完成后统一生成 SHA-256 校验和与构建来源证明。任一平台失败时不发布任何平台；全部资产先进入 Draft Release，上传完整后再一次性公开。

## 自动化

[发布目标清单](https://github.com/w0fv1/norm/blob/main/tool/cli/release-targets.json)是平台、runner、CLI 路径和 VSIX target 的唯一机器定义；打包器与 [Release 工作流](https://github.com/w0fv1/norm/blob/main/.github/workflows/release.yml)共同读取它。日常 CI 负责尽早验证 Native Image，Release 工作流只接受 `vMAJOR.MINOR.PATCH` tag。

公开版本应逐步接入 Windows Authenticode 签名以及 macOS Developer ID 签名和 notarization。签名接入前，版本说明必须明确系统可能显示来源警告。

## 版本说明

版本说明只记录该版本实际交付的语言能力、工具变化、迁移要求和已知限制。发布前必须存在由 tag 的 `major.minor` 派生出的中英文版本记录。当前实现边界见 [Norm 0.1 版本记录](/versions/0.1)，未来语言规范不作为当前编译器的交付承诺。
