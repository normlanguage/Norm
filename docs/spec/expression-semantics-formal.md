# Norm Language Specification: Expression Semantics

## Expression Model

Norm expressions are typed computations. Every expression has:

- a static type
- a runtime evaluation behavior
- possible control flow effects

The compiler resolves expression types before execution.

## Value Producing Control Flow

`if`, `for`, and `switch` may be used as expressions.

A value-producing control expression must explicitly produce a value through `break`.

Example:

```norm
String result = if enabled {
    break "enabled"
} else {
    break "disabled"
}
```

The compiler rejects paths without a value.

## No Implicit Null

Norm never inserts null as a missing expression result.

Nullable values must be explicitly produced.

```norm
String? value = if condition {
    break "hello"
} else {
    break null
}
```

