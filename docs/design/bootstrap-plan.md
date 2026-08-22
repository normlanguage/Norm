# 编译器引导计划

官方工具链使用 Java 建立完整前端和 GraalVM/Truffle 执行链。已交付范围见 [版本记录](/versions/0.1)，本文只定义通向 1.0 的结构顺序。

## 工程基础

Gradle 多项目构建统一管理 core 与 CLI，锁定 Java、Truffle 和测试依赖。SourceFile、SourceSpan、Diagnostic、格式检查和 CI 是所有后续阶段的公共基础。

## Lexer 与 Parser

Lexer 和手写 Parser 生成带完整 SourceSpan 的 AST。错误恢复必须产生稳定诊断，并让 formatter、LSP 与编译器复用同一语法结构。

## 语义模型

名称解析、名义类型、泛型约束、nullable 流分析、确定赋值和调用绑定写入 SemanticModel。实参到形参的映射只解析一次，并保留源码求值顺序。

## Bound IR

Bound IR 固化表达式类型、value/identity 类别、调用目标、控制流边和 reified 泛型信息。它是前端到后端的唯一边界，不建立第二套解释器语义。

## Truffle 后端

Lowerer 只消费 Bound IR，生成函数 CallTarget、frame slot、控制流节点和互操作边界。Native Image 打包同一 CLI 与运行时，不构成第二套后端。

## 验收

每个阶段同时提供语法、语义、运行时和真实 CLI 测试。文档代码示例参与检查，JVM 与 Native Image 的可观察行为必须一致。
