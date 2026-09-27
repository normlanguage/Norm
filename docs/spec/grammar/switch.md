# Switch Syntax

`switch` branches on enum variants, literals, null, or nominal runtime types. Each `case` uses a block; its expression form produces a result through `break value`.

```norm
String text = switch token {
    case Number(Double value) { break "number" }
    case Name(String value) { break value }
    case End { break "end" }
}
```

## Match order

The matched expression is evaluated once. Cases are tested in source order, and only the first matching case executes. There is no fallthrough. See [pattern matching](/spec/grammar/patterns) for complete pattern rules.

## Exhaustiveness

Every switch must be exhaustive. The compiler computes coverage recursively from static types and patterns: a closed enum must cover every variant, a nullable type must cover null, and open nominal types or value domains that cannot be finitely enumerated must cover remaining values with `_`. A missing branch is a compile error.

An open type hierarchy cannot enumerate every subclass statically and needs an explicit fallback:

```norm
String kind = switch shape {
    case Circle circle { break "circle" }
    case _ { break "other" }
}
```

`_` covers all unmatched values of the current type. A branch already completely covered by earlier patterns is unreachable and causes a compile error.

## Completion rules

A statement switch case may complete normally, ending the whole switch. In an expression switch, each case path that can complete normally must execute `break value`. Abnormal paths such as `return` or `throw` need no local result. All `break value` results must merge into one static type.
