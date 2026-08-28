---
title: Norm 文档
description: 学习语言、查阅规则并确认当前实现边界
pageClass: docs-hub
sidebar: false
aside: false
---

# Norm 文档

先选择阅读目的。每一类文档只回答一种问题，完整章节目录由对应侧栏维护。

<div class="docs-hub-grid">

<section>

#### Learn

第一次接触 Norm，沿着十二章连续编写可运行程序。

- [开始 Language Tour](/learn/)
- [Hello, Norm](/learn/hello)

</section>

<section>

#### Language

理解 Norm 为什么区分身份、值、有限状态、能力和受控别名。

- [认识 Norm](/guide/)
- [语言哲学](/guide/philosophy)
- [设计原则](/guide/design-principles)

</section>

<section>

#### Reference

精确查找语法、类型规则、名称解析和诊断边界。

- [Language Reference](/spec/language-spec)
- [语法参考](/spec/grammar/overview)
- [类型系统](/spec/type-system)

</section>

<section>

#### Standard Library

查找当前标准库模块、类型、函数和失败模型。

- [标准库概览](/stdlib/overview)
- [Unicode 文本](/stdlib/string)
- [I/O 与 HTTP](/stdlib/io)

</section>

<section>

#### Tooling

安装 CLI 与 VS Code，了解语义补全、导航和重命名。

- [Tooling 概览](/tooling/)
- [VS Code 开发体验](/guide/vscode)

</section>

<section>

#### Design

阅读语义模型、Canonical Core、内容身份和执行后端设计。

- [Compiler Design](/design/)
- [编译器架构](/spec/compiler-design)

</section>

<section>

#### Status

确认当前版本已经实现什么、仍缺少什么，以及采用时需要注意的边界。

- [Current Status](/status)
- [最新实现契约](/versions/)

</section>

<section>

#### Project

查看发布历史、路线图、治理与贡献入口。

- [版本索引](/versions/)
- [项目路线图](/design/roadmap)
- [社区与贡献](/community)

</section>

</div>

## 推荐路径

首次学习依次阅读 [Language Tour](/learn/) → [Standard Library](/stdlib/overview) → [Tooling](/tooling/)。评估采用时同时查看 [Status](/status)；实现语言工具或确认边界时直接进入 [Language Reference](/spec/language-spec)。
