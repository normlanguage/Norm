# LLVM Lowering Specification

## Overview
Norm IR is lowered to LLVM IR through a typed lowering phase.

Pipeline:

Norm AST -> Typed IR -> Norm SSA IR -> LLVM IR -> Object File

## Primitive Types

int, long, float, double map to LLVM integer and floating types.

## Value Types

Norm value types are lowered using structural layouts. Copy semantics are preserved by compiler generated copy operations or optimized away.

## Reference Types

Ref<T> lowers to managed references containing runtime metadata and GC tracking information.

## Method Calls

Direct calls use static dispatch. Class virtual calls use runtime method tables. Interface calls use interface dispatch tables.

## Exception Boundary

throw operations lower to runtime exception calls with landing pads.
