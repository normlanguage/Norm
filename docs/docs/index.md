---
title: Norm 文档
description: 学习 Norm 语言、标准库与应用平台
pageClass: docs-hub
sidebar: false
aside: false
---

# Norm 文档

先用 Guide 建立整体认识，再进入手册、规范和应用开发参考。

::: tip 选择正确的文档
**语言手册**适合按顺序学习，章节会逐步引入概念。**语言规范**面向已经熟悉 Norm、需要确认精确规则的读者，可以独立查阅。**标准库参考**描述库类型与 API。Web、数据库和部署内容只出现在应用平台文档中。
:::

<div class="docs-hub-grid">

<section>

#### 开始

了解语言定位、核心语义、工具体验和当前边界。

- [认识 Norm](/guide/)
- [Norm 是什么](/guide/introduction)
- [VS Code 开发体验](/guide/vscode)
- [语言哲学](/guide/philosophy)
- [设计原则](/guide/design-principles)
- [语言设计白皮书](/guide/design-whitepaper)
- [比较、取舍与发展方向](/guide/comparison-and-future)

</section>

<section>

#### 语言手册

可以从上到下连续阅读的语言教程。

- [手册介绍](/language/overview)
- [基础语法](/language/basics)
- [类型与 Null](/language/types)
- [Class、Value 与 Identity](/language/objects)
- [函数](/language/functions)
- [控制流](/language/control-flow)
- [接口](/language/interfaces)
- [泛型](/language/generics)
- [错误处理](/language/errors)

</section>

<section>

#### 语言规范

按主题独立查阅的精确语言规则。

- [语言规范](/spec/language-spec)
- [类型系统](/spec/type-system)
- [对象模型](/spec/object-model)
- [内存语义](/spec/memory-semantics)
- [Package 与导入](/spec/package-system)
- [模块系统](/spec/module-system)
- [编译器设计](/spec/compiler-design)

</section>

<section>

#### 语法参考

按语言结构查找正式语法规则。

- [语法索引](/spec/grammar/overview)
- [词法结构与关键字](/spec/grammar/lexical)
- [声明与类型](/spec/grammar/declarations)
- [表达式与语句](/spec/grammar/expressions)
- [函数、类与接口](/spec/grammar/functions)
- [操作符优先级](/spec/grammar/operators-precedence)

</section>

<section>

#### 标准库

应用程序使用的基础类型与 API。

- [标准库概览](/stdlib/overview)
- [输出 API](/stdlib/output-api)
- [I/O 基础 API](/stdlib/io)
- [字符串与集合](/stdlib/string)
- [时间与数值](/stdlib/time)
- [文件与进程](/stdlib/filesystem)
- [HTTP 与 SQL](/stdlib/http)
- [Serialization](/stdlib/serialization)
- [JSON API](/stdlib/json-api)
- [XML API](/stdlib/xml-api)
- [YAML API](/stdlib/yaml-api)
- [测试 API](/stdlib/testing-api)
- [Validation API](/stdlib/validation-api)

</section>

<section>

#### Web 应用

从请求处理到生产部署的应用平台设计。

- [Web 平台概览](/web/overview)
- [路由与 Controller](/web/routing-design)
- [依赖注入与配置](/web/dependency-injection)
- [安全与认证](/web/security)
- [数据、任务与消息](/web/database-migration)
- [可观测性与部署](/web/observability)

</section>

<section>

#### 工具与生态

了解工具链、包生态和迁移策略。

- [版本索引](/versions/)
- [实现策略决议](/design/implementation-strategy)
- [工具链开发规范](/design/toolchain-development)
- [系统运行时架构](/design/system-runtime)
- [序列化运行时架构](/design/serialization-runtime)
- [编译器引导计划](/design/bootstrap-plan)
- [生态策略](/ecosystem/strategy)
- [包管理器](/ecosystem/package-manager)
- [包注册表](/ecosystem/registry)
- [迁移策略](/ecosystem/migration)
- [技术计划](/design/technical-plan)
- [项目路线图](/design/roadmap)

</section>

<section>

#### 项目设计

语言如何演进、发布和治理。

- [性能目标](/design/performance-goals)
- [兼容性](/design/compatibility)
- [语言演进](/design/language-evolution)
- [发布流程](/design/release-process)
- [治理](/design/governance)
- [社区](/community)

</section>

</div>

## 推荐阅读路径

第一次接触 Norm，建议依次阅读：[认识 Norm](/guide/) → [Norm 是什么](/guide/introduction) → [手册介绍](/language/overview) → [类型与 Null](/language/types) → [Class、Value 与 Identity](/language/objects) → [函数](/language/functions) → [控制流](/language/control-flow)。评估采用时同时查看[比较、取舍与发展方向](/guide/comparison-and-future)和[当前版本记录](/versions/)。
