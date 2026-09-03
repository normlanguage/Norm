# 编译器引导计划

官方工具链使用 Java 建立完整前端和 Truffle 执行链。已交付范围见[版本索引](/versions/)，本文只定义通向 1.0 的结构顺序。

## 工程基础

单一 Gradle 编译器模块锁定 Java、Truffle 和测试依赖。SourceFile、SourceSpan、Diagnostic、格式检查和 CI 是所有后续阶段的公共基础。

## Lexer 与 Parser

Lexer 和手写 Parser 生成带完整 SourceSpan 的 AST。错误恢复必须产生稳定诊断，并让 formatter、LSP 与编译器复用同一语法结构。

## 语义模型

名称解析、名义类型、泛型约束、nullable 流分析、确定赋值和调用绑定写入 SemanticModel。实参到形参的映射只解析一次，并保留源码求值顺序。

## Canonical Core

Binder 固化表达式类型、value/identity 类别、调用目标、控制流边和 reified 泛型信息。CoreBuilder 将结果转换为确定性 Core IR；定义 identity 包含 canonical 内容与固定依赖，authoring 名字和源码位置分别保存在 namespace 与 occurrence metadata。

## Truffle 后端

Lowerer 只消费 `CoreCompilation`，生成函数 CallTarget、frame slot、控制流节点和互操作边界。CLI 发行包携带同一执行实现及平台 runtime。

## 验收

每个阶段同时提供语法、语义、运行时和真实 CLI 测试。文档代码示例参与检查，开发入口与正式发行包的可观察行为必须一致。
