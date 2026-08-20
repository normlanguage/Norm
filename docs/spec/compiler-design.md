# Norm Compiler Architecture

## 总体结构

Norm 编译器采用前端与后端分离设计。

```
Source
 ↓
Lexer
 ↓
Parser
 ↓
AST
 ↓
Semantic Analysis
 ↓
Typed IR
 ↓
Backend
```

## Frontend

负责：

- 语法分析
- 名字解析
- 类型检查
- 泛型解析
- overload resolution
- null analysis

## Typed IR

IR 是 Norm 语义的核心表示。

多个后端共享同一个 IR：

- Truffle interpreter
- Native compiler

## 优化方向

- copy elision
- escape analysis
- COW 优化
- inline
- dead code elimination
