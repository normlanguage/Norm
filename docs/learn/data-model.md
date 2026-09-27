# 04 Class, Value, and Interface

Not all data has the same semantics: entities retain identity, data follows value rules, and capabilities are expressed through nominal interfaces.

<<< ../../norm/tests/docs/tour/04_data_model.norm{norm}

Output:

```text
true
point
1
```

| Construct | Meaning | State | Composition |
| --- | --- | --- | --- |
| `class` | Entity with identity | Mutable fields | Single class inheritance and interface implementation |
| `value` | Data defined by its contents | Fields cannot be reassigned after construction | Interface implementation |
| `enum` | Finite alternatives that may carry data | Variant payloads | Pattern matching |
| `interface` | Capability and substitution contract | No instance fields | Multiple interface inheritance and default methods |

## Classes retain identity

Assigning, passing, and returning a class instance preserve the same object identity. Call `copy()` explicitly when a new top-level identity is needed; other objects referenced by class fields remain shared.

## Values express contents

A value can declare generic parameters and methods and implement interfaces. Its fields cannot be reassigned after construction, and it does not participate in class inheritance. Assignment, arguments, and returns follow value rules; equality and hashing recursively use field semantics.

## Interfaces express capability

A type satisfies an interface only when it explicitly declares `implements`; methods with matching names do not establish the relationship automatically. Interfaces can inherit multiple interfaces and provide default methods.

See the [object model](/spec/object-model), [class declarations](/spec/grammar/classes), and [value declarations](/spec/grammar/values) for construction, inheritance, and initialization rules.

Previous: [Functions and calls](/learn/functions). Next: [Data Enum and Switch](/learn/enum-switch).
