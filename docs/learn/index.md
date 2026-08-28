---
title: Language Tour
description: 用十二章建立完整的 Norm 语言心智模型
---

# Language Tour

用大约十五分钟认识 Norm 的核心语言模型，并运行一组由当前编译器持续验证的程序。

Norm 用不同构造表达不同语义：`class` 表达身份，`value` 表达值，`enum` 表达有限选择，`interface` 表达能力，`ref` 表达受控别名。Tour 围绕这条主线组织，不按 Parser 或 AST 类型罗列功能。

## 阅读顺序

| 章节 | 建立的认识 |
| --- | --- |
| [01 Hello, Norm](/learn/hello) | 源文件、入口和基本代码形状 |
| [02 值与绑定](/learn/bindings) | 显式类型、`var`、赋值和字面量 |
| [03 函数与调用](/learn/functions) | 返回类型、参数标签和调用顺序 |
| [04 Class、Value 与 Interface](/learn/data-model) | 身份、值与名义能力 |
| [05 数据 Enum 与 Switch](/learn/enum-switch) | 有限状态、解构和穷尽分支 |
| [06 Null 与类型推断](/learn/nullability-inference) | 可空性、期望类型和推断边界 |
| [07 集合与迭代](/learn/collections) | Array、List、Iterable 和索引 |
| [08 Lambda 与 Extension](/learn/lambdas-extensions) | 函数值、捕获和静态扩展调用 |
| [09 错误与异常](/learn/errors) | 可预期结果与异常控制流 |
| [10 引用](/learn/references) | 可寻址位置和词法生命周期 |
| [11 Annotation](/learn/annotations) | 类型化元数据与拦截行为 |
| [12 Package 与 Module](/learn/packages-modules) | 多文件程序和公开边界 |

## Tour、Reference 与 Status

Tour 负责连续学习，省略少见边界。[Language Reference](/spec/language-spec)精确定义编译器应接受、拒绝和执行的行为。[Status](/status)只描述当前发布版已经交付的能力和限制。

从[第一章：Hello, Norm](/learn/hello)开始。
