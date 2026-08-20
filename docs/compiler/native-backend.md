# Norm Native Backend Design

## Goal

Generate native executable applications.

## LLVM Backend

LLVM provides:

- mature optimization
- many architectures
- ecosystem support

Pipeline:

Norm IR -> LLVM IR -> Machine Code

## Cranelift Backend

Cranelift provides:

- fast compilation
- simple architecture
- suitable for development builds

## ABI

Norm ABI defines:

- function calling
- object layout
- exception boundary
- runtime calls

## Executable Generation

Final output:

```
app.norm
   -> compiler
   -> app.exe
```

Runtime components:

- GC
- reflection metadata
- exception runtime
- standard library runtime
