---
title: Tooling
description: Norm CLI、语言服务与编辑器集成
---

# Tooling

Norm 的 formatter、诊断和编辑器能力读取与编译器相同的语义快照。类型检查、名称解析和项目边界只实现一次。

## 当前能力

- 格式化与编译器诊断；
- 作用域和期望类型驱动的补全；
- Signature Help 与 Hover；
- 跳转定义与查找引用；
- Prepare Rename 与语义 Rename；
- 跨文件、跨 package 和标准库源码导航；
- 未保存文档参与项目分析。

结构化 API 文档复用同一语义入口，见 [API 文档导出](/zh/tooling/api-documentation)。自包含应用的输出约定见[应用构建](/zh/tooling/application-build)。

## 从示例开始

使用[开发版工具链](/zh/design/distribution-source-build)，在空目录中运行 `norm hello`，生成五个独立的 `.norm` 程序以及英文、中文说明。先运行 `norm hell.norm`，再按生成的[示例说明](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/resources/hello/README.zh-CN.md)继续体验。

`norm run main.norm` 默认不显示启动准备日志。使用 `norm run --debug main.norm`，将启动阶段、累计耗时和实际 Maven 下载写入 stderr；错误诊断始终显示，应用输出不受此选项影响。进度入口见 [RunCommand](../../../cli/compiler/src/main/java/dev/w0fv1/norm/cli/controller/RunCommand.java)，本地依赖的联网边界验证见 [JarResolverTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JarResolverTest.java)。

## VS Code

AI Agent 的规范、查询和验证导航见 [Agent 开发入口](/zh/tooling/agent)。

正式 VSIX 包含受支持平台的同版本自包含 CLI。安装、运行、项目识别和 CLI 选择见 [VS Code 开发体验](/zh/guide/vscode)。发布资产与平台矩阵见[发布流程](/zh/design/release-process)。

## 共同语义入口

```text
Source files
  → Project source set
  → Semantic model
  → diagnostics / completion / signatures
  → hover / navigation / references / rename
  → formatter / compiler
```

工具能力的当前交付边界见 [Status](/zh/status)。调试器和在线执行环境尚未进入发布版。
