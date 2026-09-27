# 03 Functions and calls

Functions are a top-level language construct. Return types, parameter types, and public parameter names together form the visible call contract.

<<< ../../norm/tests/docs/tour/03_functions.norm{norm}

Output:

```text
40
```

## Declarations

```text
ReturnType functionName(ParameterType parameterName, ...) { ... }
```

Named functions that produce a value use `return value`. A top-level function without an explicit return type is fixed to `Void`; its type is not inferred from the body.

## Argument labels

Calls with multiple arguments name their parameters:

```norm
Integer difference = subtract(left: 140, right: 100)
```

Labels select parameter slots, but argument expressions are still evaluated in source order from left to right. A single-argument call may omit the label. In a multi-argument call, a bare identifier with the same name as its parameter may use shorthand. Named and positional arguments cannot be mixed.

## Methods

Instance methods can access fields. A class method without an explicit return type is fluent: normal completion or a bare `return` returns `this`. A method that genuinely produces no result explicitly declares `Void`.

For overloads, overrides, and complete callable syntax, see the [function reference](/spec/grammar/functions) and [advanced function rules](/spec/grammar/functions-advanced).

Previous: [Values and bindings](/learn/bindings). Next: [Class, Value, and Interface](/learn/data-model).
