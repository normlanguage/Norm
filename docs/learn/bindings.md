# 02 Values and bindings

A variable binds a name to a definite static type. `var` only omits a type that can be uniquely determined from the initializer.

<<< ../../norm/tests/docs/tour/02_bindings.norm{norm}

Output:

```text
ready
2
```

## Explicit types and `var`

```norm
Integer count = 1
String name = "Norm"
var ready = true
```

Local variables must be initialized when declared. `var` does not mean dynamic typing: later assignments must still match the inferred type.

These declarations are rejected:

```norm
Integer count
var missing = null
var values = []
```

The first has no initializer; the other two have no context that uniquely determines a type.

## Basic types

Common built-in types include `Integer`, `Long`, `Float`, `Double`, `Number`, `Boolean`, `CodePoint`, `String`, and `Void`. Norm has no universal `Object` root type, and neither numbers nor strings are implicitly Boolean.

Identifiers use Unicode and participate in name comparisons in NFC form. `value`, `annotation`, and `extension` are contextual keywords only in their respective declaration positions.

See the [type system](/spec/type-system) and [literals](/spec/grammar/literals) for precise rules.

Previous: [Hello, Norm](/learn/hello). Next: [Functions and calls](/learn/functions).
