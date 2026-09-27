# 05 Data Enum and Switch

An enum defines a closed set of states. `switch` destructures those states, and the compiler checks coverage.

<<< ../../norm/tests/docs/tour/05_enum_switch.norm{norm}

Output:

```text
N-42
```

A variant may carry no data or declare one or more typed payloads. Its constructor is in the enum namespace and uses argument labels.

## Switch is an expression

An expression branch produces its result with `break value`. Norm does not implicitly use the last expression in a branch as the switch result.

Every switch must be exhaustive:

- A closed enum must cover every variant.
- A nullable type must also cover `null`.
- An open type or infinite value domain must use `_` for the remaining values.
- A case fully covered by earlier patterns is unreachable.

Inside a variant payload, patterns may use another variant, typed binding, `_`, a compatible literal, or `null`, so patterns can nest recursively. The matched expression is evaluated once. The first matching case executes alone, with no fallthrough.

For complete rules, see [pattern matching](/spec/grammar/patterns) and the [switch reference](/spec/grammar/switch).

Previous: [Class, Value, and Interface](/learn/data-model). Next: [Null and type inference](/learn/nullability-inference).
