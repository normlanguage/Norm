# 编译器

[English](README.md)

单一 Java 模块包含 Norm 前端、语义模型、规范化 Core IR、执行运行时、项目生命周期、宿主平台集成、CLI 和 Language Server。包边界与架构测试维护内部依赖方向。

共享的编译器与后端契约位于 `dev.w0fv1.norm.abi`。语义层只依赖语法、值、诊断与 ABI 契约；Builtin 清单通过 `BuiltinSemanticIndex` 使用语义类型，不把清单所有权反向泄漏到语义模型。

`norm` 入口提供帮助、版本查询、`run`、文档导出与基于 stdio 的 `lsp` 服务。LSP 模式下，标准输出只承载协议流量；编辑器适配器使用相同的编译器诊断和语言服务。
