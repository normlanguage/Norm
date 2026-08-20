# Norm Intermediate Representation Design

## Purpose

Norm IR is the shared boundary between the language frontend and execution backends.

Targets:

- Truffle interpreter backend
- GraalVM optimization backend
- Future native compiler backend

The frontend should decide semantics before lowering into IR.

## Pipeline

```
Source
 ↓
AST
 ↓
Typed AST
 ↓
Norm IR
 ↓
Backend
```

## IR Principles

Norm IR preserves:

- static types
- nullability information
- value/reference distinction
- generic metadata
- exception behavior

## Future Optimizations

- constant folding
- escape analysis
- copy elimination
- COW optimization
- inlining
- dead code elimination
