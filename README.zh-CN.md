# Norm

<p align="center"><img src="docs/public/brand/norm.svg" alt="Norm 标志" width="144"></p>

[English](README.md)

Norm 是静态类型、面向应用的编程语言项目，仓库包含语言规范与编译器引导实现。

Norm 用不同的语言构造表达不同的语义：`class` 表达身份，`value` 表达数据，`enum` 表达可选状态，`interface` 表达能力，`ref` 表达受控别名。

## 状态

许可协议：[MPL-2.0](LICENSE)。适用范围与源码提供方式见 [LICENSING.md](LICENSING.md)。

**持续开发中。** Norm 源码仍是编写时的源形式；编译器使用确定性的内容寻址 Core IR，管理固定定义身份、依赖跟踪、持久化定义存储与 Truffle 产物复用。当前实现契约以[版本索引](https://normlanguage.github.io/Norm/zh/versions/)为准。

## 构建

```shell
./gradlew :compiler:installRuntimeDist
./build/compiler/norm-runtime/bin/norm --version
./build/compiler/norm-runtime/bin/norm run cli/compiler/scripts/fixtures/hello.norm
```

Gradle Wrapper 使用 Java 25 构建。在 Windows 上运行 `.\gradlew.bat :compiler:installRuntimeDist`，通过 `build\compiler\norm-runtime\bin\norm.bat` 使用 CLI。发行资产写入 `build/distributions`；参见[源码构建设计](docs/zh/design/distribution-source-build.md)。

正式版本提供自包含的 `norm` 发行包，以及内含对应发行包的 VS Code 扩展。支持的平台与验收要求见[发布流程](https://normlanguage.github.io/Norm/zh/design/release-process)。

## 文档

VitePress 站点分别提供连续的语言教程、精确的语言参考、当前标准库 API、工具链与编译器设计，以及发布状态。主要示例会编译、运行，并与配套输出文件比对。

GitHub Pages 部署后可访问：

**https://normlanguage.github.io/Norm/zh/**

## 仓库结构

```text
cli/                         命令行产品
  compiler/                  Java 编译器、运行时、CLI 和语言服务
  extensions/                编辑器扩展
norm/stdlib/                  使用 Norm 编写的标准库
norm/tests/                   可执行的 Norm 测试程序
docs/                         文档站点
norm/tests/docs/              可执行的文档示例
```

## 实现策略

Norm 官方编译器使用 Java 实现为一个物理模块，包结构保留编译与执行的领域边界。Truffle 是唯一的官方执行后端。发行包包含对应平台的运行时，使 CLI 无需系统 Java 即可加载独立发布的 Java 库。Zig 不属于编译器或标准库平台适配层。

前端在后端降级之前产生规范化 Core IR。源码中的名称和位置信息与语义定义身份分离，Truffle 仅从 Core 接收程序输入。参见[编译器架构](https://normlanguage.github.io/Norm/zh/spec/compiler-design)和[实现策略](https://normlanguage.github.io/Norm/zh/design/implementation-strategy)。

## 仓库边界

本仓库维护 Norm 语言、标准库、编译器／CLI 和 VS Code 插件，以及它们的文档与测试。

应用示例与端到端验收见 [examples](https://github.com/normlanguage/examples)。适配包在 [normlanguage](https://github.com/normlanguage) 组织下各自维护、测试和发布；编译器只负责通用包解析、Java 互操作与 Native Image 集成。
