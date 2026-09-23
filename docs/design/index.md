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
Runtime: execution contracts → Truffle → platform adapter
```

## 阅读入口

- [编译器架构](/spec/compiler-design)：完整流水线、身份边界和增量模型；
- [实现策略决议](/design/implementation-strategy)：技术栈与依赖方向；
- [工具链开发规范](/design/toolchain-development)：模块职责与验证约束；
- [发行版源码构建架构](/design/distribution-source-build)：Maven 唯一构建入口、发行版离线构建与发布等价验收；
- [块调用链设计与落地方案](/design/block-call-chains)：受限省点号闭包链的决策、实现索引、迁移与验收；
- [Agent 工具设计](/design/agent-tooling)：明确性、强引用、上下文效率与验证契约；
- [系统运行时架构](/design/system-runtime)：I/O、资源和平台适配；
- [序列化运行时架构](/design/serialization-runtime)：结构元数据与 mapper；
- [Java Library Adapter](/design/java-library-adapters)：单根 JAR、普通 Module 身份、内容寻址与发布边界；
- [Vaadin 适配计划与落地方案](/design/vaadin-integration)：普通字段响应页面、独立 Jetty、Spring 集成与分阶段验收；
- [编译器引导计划](/design/bootstrap-plan)：自举边界。
- [应用启动性能](/design/startup-performance)：源码运行基线、制品复用边界与验收。

性能目标只记录可验证预算，不从架构反推未经测量的性能结论。当前对外能力见 [Status](/status)。
