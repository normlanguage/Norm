---
title: 认识 Norm
description: 从语言定位、核心语义到当前工具链，建立对 Norm 的完整认识
---

# 认识 Norm

Norm 是一门静态强类型、面向应用开发的编程语言。它保留类型前置、大括号、class、interface 和 exception 等熟悉结构，重新整理了容易让大型程序失去可预测性的几条边界：什么是结构数据，什么拥有对象身份，控制流从哪里产生值，泛型信息能否进入运行时，以及框架能力能否在不引入宏的情况下扩展。

Guide 不逐项教授语法，也不代替语言规范。它帮助你先回答三个问题：Norm 的代码读起来是什么感觉，它为什么做出这些选择，当前实现已经能够承担什么工作。

## 建议阅读路径

### 第一次接触 Norm

从[Norm 是什么](/guide/introduction)开始。它用一段可运行代码串起 value、命名参数、穷尽 switch、extension function 和结构序列化，并说明当前实现边界。

接着进入[语言手册](/language/overview)，按顺序学习类型、对象、函数、控制流、接口、enum、泛型与错误处理。

### 想理解设计取舍

[语言哲学](/guide/philosophy)解释 Norm 为什么把“语义可见性”放在语法糖之前；[设计原则](/guide/design-principles)给出新功能进入语言时必须满足的工程准则。

[设计白皮书](/guide/design-whitepaper)把类型系统、数据模型、Annotation、系统运行时和官方工具链放进同一张图里，适合希望系统理解 Norm 的读者。

### 正在评估或使用 Norm

[比较、取舍与发展方向](/guide/comparison-and-future)从具体维度比较 Java、Kotlin、Rust、Go 和 TypeScript，并明确 Norm 当前仍不成熟的部分。

[VS Code 开发体验](/guide/vscode)说明如何安装包含原生 CLI 的扩展、创建文件、运行程序以及处理项目识别问题。

## 文档层次

| 文档 | 回答的问题 |
| --- | --- |
| Guide | Norm 是什么，为什么这样设计，是否适合我的项目？ |
| [语言手册](/language/overview) | 日常代码应该怎样写？ |
| [语言规范](/spec/language-spec) | 编译器必须接受、拒绝和执行什么？ |
| [标准库](/stdlib/overview) | 已有的类型与 API 怎样使用？ |
| [版本记录](/versions/) | 当前发布版真正实现到哪里？ |

语言规范描述长期语义，版本记录描述当前工具链。两者存在差异时，不把尚未实现的规范目标包装成已经可用的产品能力。

下一篇：[Norm 是什么](/guide/introduction)。
