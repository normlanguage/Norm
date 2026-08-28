---
title: Compiler Design
description: Norm 前端、Canonical Core 与 Truffle 后端
---

# Compiler Design

Norm 把作者态语义与可执行内容身份分开。源码先形成共享的语义模型，再冻结为规范化、内容寻址的 Core；Truffle 只执行已经解析的 Core。

```text
Source
  ↓
Syntax
  ↓
Semantic Model
  ↓
Bound Representation
  ↓
Canonical Core IR
  ↓
execution-api
  ↓
Truffle Backend
```

## 阅读入口

- [编译器架构](/spec/compiler-design)：完整流水线、身份边界和增量模型；
- [实现策略决议](/design/implementation-strategy)：技术栈与依赖方向；
- [工具链开发规范](/design/toolchain-development)：模块职责与验证约束；
- [系统运行时架构](/design/system-runtime)：I/O、资源和平台适配；
- [序列化运行时架构](/design/serialization-runtime)：结构元数据与 mapper；
- [编译器引导计划](/design/bootstrap-plan)：自举边界。

性能目标只记录可验证预算，不从架构反推未经测量的性能结论。当前对外能力见 [Status](/status)。
