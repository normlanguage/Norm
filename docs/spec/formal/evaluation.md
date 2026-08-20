# Norm Formal Semantics: Core Evaluation Model

Norm uses deterministic evaluation. Expressions produce values; statements perform actions.

Control expressions (`if`, `for`, `switch`) produce values only through explicit `break value`.

Every expression has:
- static type
- runtime representation
- evaluation order

Norm never inserts null automatically. Nullable results must explicitly return null.

Example:

```norm
User? user = if exists {
    break loadedUser
} else {
    break null
}
```
