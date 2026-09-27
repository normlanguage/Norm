# Memory semantics

Norm specifies observable values and identity, not object layout or a memory-management algorithm. The concrete assignment rules are defined by [value and identity semantics](/spec/value-identity-semantics).

## Observable behavior

- A value is logically independent after assignment, argument passing, and return.
- Class operations share object identity.
- `class.copy()` creates a new top-level object identity.
- Values compare structurally; classes compare by identity.
- Arguments and subexpressions are evaluated from left to right in source order.

Implementations may use eager copying, copy-on-write, structural sharing, and escape analysis. Optimizations must not change these behaviors.

## Lifetime

Programs do not manually release ordinary values or class instances. Reachable objects must remain valid; external resources are managed through explicit scoped APIs. Ordinary programs are not required to recover reliably from out-of-memory or stack-overflow conditions.

Addressable locations, escape restrictions, and lexical lifetime for `ref<T>` are defined together in the [reference rules](/spec/grammar/references).
