# Expression semantics

An expression is a computation with a static type that produces a value at runtime. A statement produces only a completion effect; a control construct becomes an expression only when used in a value position and when its paths satisfy the required rules.

## Basic judgments

`Γ ⊢ e : T` means expression `e` has type `T` in static environment `Γ`. Evaluation is written `⟨e, Σ⟩ ⇓ ⟨v, Σ'⟩`, where `v` must satisfy `T`.

## Evaluation order

- Operands and arguments are evaluated from left to right in source order.
- `&&` and `||` short-circuit.
- An `if` evaluates only its selected branch.
- A switch evaluates its matched value once and has no case fallthrough.
- Assignment updates its target only after the right-hand side evaluates successfully.

## Control expressions

```norm
String result = if enabled {
    break "enabled"
} else {
    break "disabled"
}
```

In an expression context, every normally completing path must produce `break value`. Return and Throw are abnormal completions and need no additional value. Every switch must also prove its patterns exhaustive at compile time. The compiler computes a common static type for all Value completions and rejects the program when there is no unique type.

## No implicit null

A missing `else` or an exhausted `for` does not automatically yield null. Exhaustiveness checking excludes unmatched switch paths at compile time. Other control expressions must complete their paths or explicitly return a nullable type.

## Calls and construction

Constructor and ordinary function calls both resolve named arguments before evaluating them in source order. Value arguments become logically independent values; class arguments preserve object identity. Default arguments are evaluated in the callee's declaration environment; the current draft requires them to be compile-time constants.

## Errors

Static errors include using a `Void` value, a path without a result, a non-`Boolean` condition, an ambiguous call, and unsafe assignment. Runtime exceptions do not change the expression's static type; they propagate as Throw completions.
