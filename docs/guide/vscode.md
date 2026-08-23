# VS Code 支持

Norm 的 VS Code 扩展位于 `tool/cli/extensions/vscode`。它负责编辑器接入，语言分析仍由 Java 工具链中的 `norm lsp` 完成。

## 当前能力

- `.norm` 文件识别，以及 nullable、泛型、package/import、`public`、`private` 等语法的 TextMate 高亮；
- 括号、引号、行注释和块注释编辑行为；
- 直接显示 Norm 编译器诊断及源码范围；
- 根据语法位置和期望类型排序关键字、局部值、函数、类型与代码模板；
- 在未闭合的返回语句、变量初始化和调用参数中继续提供补全与参数提示；
- 根据参数化类型补全对应的容器成员，并显示替换后的泛型签名；
- nullable receiver 的 safe access 补全、类型级集合成员补全和重载签名提示；
- 补全 module 导出的跨 package 声明时自动加入 import；
- enum 成员补全；
- 泛型声明签名、类型参数、核心类型、标准库和用户声明的悬停说明；
- 泛型类型参数的补全、跳转定义、引用和重命名；
- 遵循 module 导出、顶层文件私有声明与 class 私有成员可见性的跨文件诊断、跳转定义、引用和重命名。

## 安装发布版本

从 GitHub Release 下载唯一的 `norm-language-support-vMAJOR.MINOR.PATCH.vsix`，在 VS Code 中执行 **Extensions: Install from VSIX...**。发布版扩展已包含所有受支持平台的同版本 `norm` 可执行文件，会自动选择当前平台，不要求预装 Java、GraalVM 或单独配置 CLI。

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

解析顺序为显式设置的 `norm.cli.path`、扩展内置 CLI、当前工作区构建、扩展源码树中的开发构建、系统 `PATH`。发布版使用内置 CLI；只有工具链开发、自动化测试或自定义 CLI 时才需要覆盖它。

## 实现边界

扩展通过微软的 `vscode-languageclient` 启动 `norm lsp`，Java 端使用 Eclipse LSP4J 处理 LSP/JSON-RPC。补全、参数提示及其排序由编译器语义快照统一计算，扩展不维护第二套语言规则。语法高亮不依赖语言服务器，因此 CLI 未配置时仍可使用；其他语言能力需要 CLI。

当前扩展的语言能力边界见 [Norm 0.3 版本记录](/versions/0.3)。格式化和调试器由后续工具版本交付。
