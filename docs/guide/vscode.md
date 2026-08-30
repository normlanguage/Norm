# VS Code 开发体验

Norm 的官方 VS Code 扩展包含当前版本的原生 `norm` CLI。安装一个 VSIX 后即可获得语法高亮、编译器诊断、格式化、补全、导航和运行命令，不需要另行安装 Java 或 GraalVM。

## 安装

1. 打开 [Norm 最新 Release](https://github.com/w0fv1/Norm/releases/latest)。
2. 下载唯一的 `norm-language-support-vMAJOR.MINOR.PATCH.vsix`。
3. 在 VS Code 命令面板执行 **Extensions: Install from VSIX...**。
4. 选择下载的文件，重新加载窗口。

通用 VSIX 同时包含 Windows x64、Linux x64 与 macOS ARM64 的同版本 CLI，扩展会根据当前平台选择正确的可执行文件。

如果还需要在终端运行 `norm`，从同一 Release 下载对应平台的 CLI 压缩包，解压后把可执行文件目录加入 `PATH`。不要混用不同 Release 的扩展和 CLI。

## 第一个文件

新建 `hello.norm`：

```norm
main() {
    String language = "Norm"
    printLine("Hello, " + language)
}
```

保存后可以：

- 点击编辑器右上角的运行按钮；
- 在命令面板执行 **Norm: Run Current File**；
- 使用 `Ctrl+F5`，macOS 使用 `Cmd+F5`。

扩展会先保存当前项目中已打开的 Norm 文件，再启动程序。输出进入专用任务终端，编译错误同时显示在编辑器和 Problems 面板。

## 编辑器能力

### 编写代码

- `.norm` 文件、关键字、类型、nullable、泛型、Annotation 与 extension function 高亮；
- 括号、引号和缩进编辑行为；
- 保存时使用编译器 formatter；
- 在未完成的返回语句、变量初始化和调用参数中继续提供建议；
- 根据期望类型和作用域排序局部值、函数、类型、enum variant 与模板。

### 理解类型与调用

- 参数提示显示命名参数、重载和替换后的泛型签名；
- nullable receiver 提供 safe access 补全；
- 容器成员按实际类型参数显示；
- 显式导出的 extension function 可以成员式补全，并自动加入 import；
- `Class<T>`、`Field<Owner, Value>`、`Function<Signature>`、Annotation 生命周期及 JSON/XML/YAML API 提供补全、悬停和跳转。

### 浏览项目

- 跨文件与跨 package 跳转定义、查找引用和语义重命名；
- module export、文件私有声明和 class private 成员使用编译器可见性规则；
- 补全其他 package 的公开声明时生成精确 import edit；
- 诊断、导航和补全会跟随未保存的内存文档更新。

这些能力都由 `norm lsp` 的编译器语义快照计算。扩展不复制一套类型检查或名称解析逻辑。

在 Norm 源码仓库中编辑 `norm/stdlib/std` 时，Language Server 会把磁盘文件作为内置标准库同一声明身份的源码覆盖层分析；它不会再加载一个重名的用户 `std` 模块。

## 单文件与项目

没有 package 声明的 `.norm` 文件可以作为独立脚本运行。带 package 的应用应放在 Norm 项目中，并由根 `module.norm` 描述 module 与源码结构。

如果一个带 package 的文件被单独放在任意目录，编辑器无法凭文件名猜出完整模块图。打开包含 `module.norm` 的项目目录，让 CLI 和 Language Server 使用相同的项目根。

模块规则见[模块系统](/spec/module-system)。

## CLI 选择

正式扩展默认使用自身包含的原生 CLI。只有开发 Norm 工具链时，才需要设置 `norm.cli.path`。候选 CLI 必须与扩展版本一致；同一基础版本的开发构建可以带 `-SNAPSHOT`。不匹配的配置会被跳过，并在 Norm Language Server 输出中说明原因。

正式扩展的解析顺序是：

1. 版本匹配的 `norm.cli.path`；
2. 扩展包内同版本的 JVM 或原生 CLI；
3. 当前工作区和扩展源码树中的同版本开发构建；
4. 系统 `PATH` 中的同版本 `norm`。

通过 F5 启动扩展开发宿主时，工作区构建优先于扩展包内容。Language Server 与运行命令复用同一次选择，不会分别启动两个工具链版本。

设置路径后执行 **Norm: Restart Language Server**，确保窗口不再使用旧进程。

## 常见问题

### 文件有高亮，但没有诊断或补全

TextMate 高亮不需要语言服务器，其余功能需要 CLI。先执行 **Norm: Restart Language Server**；仍然失败时检查 Output 面板中的 Norm 日志，以及 `norm.cli.path` 是否指向当前平台可执行文件。

### 终端可以运行，扩展仍然使用旧版本

检查 **Norm Language Server** 输出中的 CLI 路径和版本。`norm.cli.path` 指向其他版本时，扩展会使用包内的匹配版本，并显示一次提示；需要开发其他版本时，应使用对应版本的扩展开发宿主。

### 跨文件 import 或补全缺失

确认 VS Code 打开的是包含 `module.norm` 的项目根，目标声明是 public，并由 module 导出。单文件脚本之间不会因为处于同一目录就自动组成项目。

### 运行命令使用了错误目录

`norm.run.workingDirectory` 可以选择工作区目录或当前文件目录。项目程序通常使用工作区目录，依赖同目录资源的独立脚本可以选择文件目录。

## 开发扩展

只有修改 Norm 编译器或 VS Code 扩展时才需要源码开发包。完整构建、测试和本地 VSIX 流程以 [`cli/extensions/vscode/README.md`](https://github.com/w0fv1/Norm/blob/main/cli/extensions/vscode/README.md) 为准；发布平台、资产和验收要求以[发布流程](/design/release-process)为准。

当前语言服务的精确边界见[版本索引](/versions/)。调试器尚未进入发布版。

下一篇：[语言哲学](/guide/philosophy)。
