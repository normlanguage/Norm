# LLVM ABI Table

Norm native ABI defines how values cross the machine boundary.

| Norm Type | Native Representation |
|---|---|
| bool | i1 |
| int | i32 |
| long | i64 |
| float | f32 |
| double | f64 |
| Ref<T> | managed pointer |
| value | flattened aggregate when possible |
| class | runtime object pointer |

Function calls use target ABI rules. Runtime calls use explicit hidden metadata parameters.
