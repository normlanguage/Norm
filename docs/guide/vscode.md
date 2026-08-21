# VS Code 支持

Norm 的 VS Code 扩展位于 `tool/cli/extensions/vscode`。它负责编辑器接入，语言分析仍由 Java 工具链中的 `norm lsp` 完成。

## 当前能力

- `.norm` 文件识别和 TextMate 语法高亮；
- 括号、引号、行注释和块注释编辑行为；
- 直接显示 Norm 编译器诊断及源码范围；
- 关键字、内置函数、代码片段和当前文件声明补全；
- 根据变量声明类型补全无泛型容器成员；
- enum 成员补全；
- 核心类型和内置函数的悬停说明。

## 安装发布版本

从同一 GitHub Release 下载与操作系统匹配的 VSIX，在 VS Code 中执行 **Extensions: Install from VSIX...**。发布版扩展已包含同版本 `norm` 可执行文件，不要求预装 Java、GraalVM 或单独配置 CLI。

如果需要让终端直接执行 `norm run example.norm`，再下载同一 Release 中对应平台的 CLI 压缩包，解压并将可执行文件所在目录加入 `PATH`。

受支持的平台与产物名称见[发布流程](/design/release-process)。

## 从源码开发

先构建 CLI 安装目录：

```powershell
.\gradlew.bat :cli:installDist
```

再构建 VSIX：

```powershell
cd tool/cli/extensions/vscode
npm install
npm run package
```

在 VS Code 中执行 **Extensions: Install from VSIX...**，选择生成的 VSIX。开发版扩展会从当前 Norm 仓库自动查找：

```text
<仓库>\tool\cli\app\build\install\norm\bin\norm.bat
```

macOS 或 Linux 使用同目录下的 `norm`。

解析顺序为显式设置的 `norm.cli.path`、`NORM_CLI` 环境变量、扩展内置 CLI、当前工作区构建、系统 `PATH`。发布版使用内置 CLI；只有工具链开发、自动化测试或自定义 CLI 时才需要覆盖它。

## 实现边界

扩展通过微软的 `vscode-languageclient` 启动 `norm lsp`，Java 端使用 Eclipse LSP4J 处理 LSP/JSON-RPC。语法高亮不依赖语言服务器，因此 CLI 未配置时仍可使用；诊断、补全和悬停则需要 CLI。

当前扩展的语言能力边界见 [Norm 0.1 版本记录](/versions/0.1)。跨文件索引、跳转定义、重命名、格式化和调试器由后续工具版本交付。
