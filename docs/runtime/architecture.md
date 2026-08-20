# 总体架构

```text
Norm Source
   ↓
Lexer / Parser
   ↓
Semantic AST
   ↓
Name + Type Resolution
   ↓
Typed IR
   ├─→ Truffle Backend → Interpreter / Graal JIT
   └─→ Native Backend  → future executable
```

Typed IR 是关键边界：Norm 的 null safety、reified generics、值语义、Ref 和反射不能由 JVM 类型模型替代。

计划模块：`compiler/`、`runtime/`、`truffle/`、`stdlib/`、`cli/`。
