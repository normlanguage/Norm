---
title: Status
description: Norm 当前已交付能力、成熟度和实现边界
---

<script setup>
import { currentRelease } from './.vitepress/release'
</script>

# Status

当前正式版本是 Norm {{ currentRelease }}。本页只描述当前工具链，长期语言规则见 [Language Reference](/spec/language-spec)，逐版本交付记录见[版本索引](/versions/)。

## 成熟度标记

| 标记 | 含义 |
| --- | --- |
| Stable | 已进入当前发布契约并有自动化验收 |
| Experimental | 已实现，但公开形状仍可能调整 |
| Internal | 工具链内部使用，尚未承诺公共入口 |
| Planned | 设计或方向已经记录，当前不可使用 |

## Language

| 能力 | 状态 | 事实入口 |
| --- | --- | --- |
| Class、Value、Interface | Stable | [对象模型](/spec/object-model) |
| 数据 Enum 与穷尽 Switch | Stable | [Enum 与 Switch](/spec/grammar/switch) |
| Nullable、`?.`、`??` 与控制流收窄 | Stable | [类型系统](/spec/type-system) |
| 泛型类型、函数、方法与双向推断 | Stable | [类型推断](/spec/type-inference) |
| Lambda、函数值与声明引用 | Stable | [函数高级规则](/spec/grammar/functions-advanced) |
| Extension function | Stable | [函数参考](/spec/grammar/functions#extension-function) |
| `ref<T>` 与词法生命周期 | Stable | [引用参考](/spec/grammar/references) |
| `Class<T>` 与类型化声明引用 | Stable | [声明引用与反射](/spec/declaration-references) |
| Annotation、`@Document` 与类型化拦截器 | Stable | [Annotation 规范](/spec/annotations) |
| Package、Module 与跨文件可见性 | Stable | [模块系统](/spec/module-system) |
| 类型化字符串插值 | Stable | [字面量](/spec/grammar/literals) |
| `//` 与 `/* */` 源码注释 | Planned | 当前 Lexer 将标记解析为运算符 token |

## Standard Library

| 范围 | 状态 |
| --- | --- |
| Core 类型、集合、Unicode 文本、Math、Time | Stable |
| 流式 I/O、文件系统与资源生命周期 | Stable |
| HTTP client | Stable |
| JSON、XML、YAML 与统一结构映射 | Stable |
| Validation 与 Testing | Stable |
| 自动映射 value | Stable |
| 自动映射 class identity、对象图、循环引用和多态 | Planned |
| HTTP server | Planned |

标准库的当前源码模块只有 `std.annotation`、`std.collections`、`std.core`、`std.filesystem`、`std.http`、`std.io`、`std.json`、`std.math`、`std.serialization`、`std.system`、`std.testing`、`std.text`、`std.time`、`std.validation`、`std.xml` 和 `std.yaml`。未对应这些源码模块的设计页不代表已交付 API。

## Tooling

| 能力 | 状态 |
| --- | --- |
| 自带 Java runtime 的 CLI 与 JVM 开发入口 | Stable |
| Formatter、诊断、补全、Signature Help、Hover | Stable |
| 跳转定义、查找引用、Prepare Rename、Rename | Stable |
| 标准库只读源码导航 | Stable |
| 官方 VS Code VSIX | Stable |
| 调试器 | Planned |
| 在线 Playground | Planned |

## 已知边界

- 自动序列化当前只处理 `value`；
- `private` 是源文件级边界；
- 泛型参数保持 invariant，不支持 raw type；
- ref 不能进入字段、容器、泛型实参、返回类型或 Lambda 捕获；
- 当前没有 HTTP server、调试器或在线执行环境。

采用决策应以[最新版本实现契约](/versions/)和实际验收程序为准。
