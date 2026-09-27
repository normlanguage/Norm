# 06 Null and type inference

Absence is part of the type. Inference uses context to reduce repetition without becoming dynamic typing or unfounded guessing.

<<< ../../norm/tests/docs/tour/06_nullability_inference.norm{norm}

Output:

```text
seven
missing
```

## Nullability

`String` is always non-null; only `String?` can hold `null`. The nullability of a collection and that of its elements are expressed separately:

```norm
List<String>? optionalNames = null
List<String?> names = ["Norm", null]
```

`?.` stops member access when the receiver is null. `??` evaluates its fallback only when the left side is null. An explicit null check can narrow a type within control flow.

## Expected types

Collection literals, generic calls, and diamond constructors use both arguments and the expected result:

```norm
Array<Integer> fixed = [1, 2, 3]
List<Integer> dynamic = [1, 2, 3]
List<Pair<Integer, String>> values = List<>()
```

Raw generic types are invalid, and type parameters are currently invariant. `null`, an empty collection, and an unconstrained `List<>()` cannot determine a type without context; failed inference produces a diagnostic.

See [type inference](/spec/type-inference) and the [generics reference](/spec/grammar/generics) for algorithms and boundaries.

Previous: [Data Enum and Switch](/learn/enum-switch). Next: [Collections and iteration](/learn/collections).
