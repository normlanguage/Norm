# Norm ABI Calling Convention

## Overview
Norm ABI defines the boundary between generated native code and runtime services.

## Primitive Mapping

| Norm | ABI |
|---|---|
| bool | i8 |
| int | i32 |
| long | i64 |
| float | f32 |
| double | f64 |
| Ref<T> | managed pointer |

## Parameter Passing

Small primitive values are passed through registers according to target ABI. Large values use hidden storage references.

## Return Values

Primitive values return directly. Large value objects return through caller allocated storage.

## Runtime Calls

Runtime calls include:
- allocation
- GC barrier
- exception throw
- reflection lookup
