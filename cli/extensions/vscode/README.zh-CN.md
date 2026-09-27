# VS Code 的 Norm 语言支持

[English](README.md)

<p align="center"><img src="images/norm-256.png" alt="Norm 标志" width="128"></p>

扩展提供语法高亮、编译器诊断、类型感知补全、签名帮助、自动导入、悬停、定义跳转、引用查找、重命名，以及通过 `norm run` 执行。Extension function、反射、Annotation 协议和数据格式库使用与 Norm 其他部分相同的编译器语言服务。

各平台发行包包含匹配的自包含 Norm CLI 和 Java runtime，无须另装 Java 或配置单独的服务器。

在 Norm 编辑器中使用播放按钮、运行 `Norm: Run Current File`，或按 `Ctrl+F5`，即可保存当前源文件并在专用 VS Code task 终端中执行。Norm 设置可通过 `Norm: Open Settings` 打开。

## 开发

1. 从仓库根构建 CLI 发行目录：

   ```powershell
   $version = (Get-Content cli/extensions/vscode/package.json | ConvertFrom-Json).version
   .\gradlew.bat :compiler:installRuntimeDist "-PnormVersion=$version"
   ```

2. 在 VS Code 中打开 Norm 仓库。开发 Extension Host 会自动发现：

   ```text
   <repository>\build\compiler\norm-runtime\bin\norm.bat
   ```

   使用其他目录布局时，明确设置 `norm.cli.path`。Norm 源码工作区可使用同一主次版本线上的更新补丁版本；显式配置、内置与 `PATH` 上的 CLI 均必须与扩展精确匹配。状态栏显示所选版本和来源。Language Server 诊断与 `Norm: Run Current File` 使用同一个已验证 CLI。

3. 安装依赖并编译扩展：

   ```powershell
   npm install
   npm run compile
   ```

4. 在 VS Code 中打开此目录并按 F5。`npm run package:local` 会重建当前 JVM CLI 发行目录，生成内含该服务端、可直接安装的 `norm-language-support-<version>-local.vsix`。

通用发行包包含 `cli/compiler/release-targets.json` 中每个目标生成的 `bin/` 资产：

```powershell
npm run package -- <version> <binaries-directory> <output.vsix>
```

运行 `npm run test:extension` 执行真实 Extension Host 测试；它会在启动 VS Code 前构建当前 CLI 发行目录。运行 `npm run smoke:lsp` 验证 stdio 协议握手。

扩展仅是 VS Code 适配器。语言分析仍由 Java 编译器承担，并通过编辑器无关的 Language Server Protocol 暴露。

许可范围和源码可用性见仓库[许可说明](../../../LICENSING.zh-CN.md)。
