# LLVM Type Mapping Specification

## Overview

Norm IR is lowered to LLVM IR through an explicit type mapping phase.

| Norm | LLVM |
|---|---|
| bool | i1 |
| byte | i8 |
| short | i16 |
| int | i32 |
| long | i64 |
| float | float |
| double | double |
| decimal | runtime decimal struct |
| Ref<T> | managed pointer |
| class | pointer + metadata |
| value | LLVM aggregate |

## Object Lowering

class values are lowered into runtime-managed structures containing:

- type metadata pointer
- GC information
- object data
- method dispatch metadata

## Method Calls

Direct calls use LLVM call instructions.

Virtual calls use runtime dispatch tables.

Interface calls use interface tables.
