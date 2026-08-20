# Norm Backend Contract

The backend converts typed Norm IR into executable representations.

Pipeline:

Source -> AST -> Typed IR -> Optimized IR -> Backend -> Executable

Supported targets:

## Truffle

Development execution and JIT optimization.

## LLVM

Production native compilation.

## Cranelift

Fast compilation and incremental builds.

Every backend implements:
- value lowering
- function calls
- object allocation
- exceptions
- runtime calls
