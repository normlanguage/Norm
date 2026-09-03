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

结构化 API 文档复用同一语义入口，见 [API 文档导出](/tooling/api-documentation)。

## VS Code

正式 VSIX 包含受支持平台的同版本自包含 CLI。安装、运行、项目识别和 CLI 选择见 [VS Code 开发体验](/guide/vscode)。发布资产与平台矩阵见[发布流程](/design/release-process)。

## 共同语义入口

```text
Source files
  → Project source set
  → Semantic model
  → diagnostics / completion / signatures
  → hover / navigation / references / rename
  → formatter / compiler
```

工具能力的当前交付边界见 [Status](/status)。调试器和在线执行环境尚未进入发布版。
