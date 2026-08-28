---
title: Language
description: Norm 的语言定位、设计原则与工程取舍
---

# Language

Norm 是一门静态强类型、面向应用开发的语言。它保留类型前置、大括号、class、interface 和 exception 等熟悉结构，把影响共享、值流和运行时行为的语义放回声明与调用中。

## 设计主线

```text
class       identity
value       data
enum        alternatives
interface   capability
ref         controlled aliasing
```

这五种构造不是同一种对象模型的语法别名。它们分别固定身份、可变性、有限状态、名义能力和位置引用的边界；函数、泛型、Annotation、标准库和工具链都在这些边界上组合。

## 阅读入口

| 目的 | 文档 |
| --- | --- |
| 连续学习语言 | [Language Tour](/learn/) |
| 理解设计价值 | [语言哲学](/guide/philosophy) |
| 判断新功能是否符合方向 | [设计原则](/guide/design-principles) |
| 系统理解语言与运行时 | [语言设计白皮书](/guide/design-whitepaper) |
| 与其他语言按具体维度比较 | [比较、取舍与方向](/guide/comparison-and-future) |
| 查找编译器精确规则 | [Language Reference](/spec/language-spec) |
| 确认当前实现成熟度 | [Status](/status) |

Guide 只解释稳定设计意图，不重复教程步骤或规范条文。当前发布版与长期规范不一致时，以 [Status](/status) 和对应版本契约判断可用性。

下一篇：[语言哲学](/guide/philosophy)。
