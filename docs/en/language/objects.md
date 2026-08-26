# Class, Value, and Identity

Class instances have identity. Primitives, enums, and built-in containers are values. See [Value and Identity Semantics](/en/spec/value-identity-semantics) for the normative rules.

## Classes preserve identity

```norm
class Counter {
    Integer value

    Void increment() {
        value = value + 1
    }
}

Counter first = Counter(value: 0)
Counter second = first
second.increment()
printLine(first.value)
```

The program prints `1`: both variables refer to the same Counter. Parameters and return values follow the same rule.

Call `copy()` when a new top-level identity is required. Value fields become independent, while class fields still refer to the same nested objects.

## Construction and inheritance

A class may declare one same-named constructor without a return type or visibility modifier. A subclass uses `extends` for single inheritance and calls `super(...)` first in its constructor. Public methods override by signature and dispatch dynamically. See [Class declarations](/spec/grammar/classes) for the normative rules.

## Containers are values

Copying a built-in container creates an independent structure. Class elements within that structure retain their identity. Values compare structurally; classes compare by identity.

## User-defined values

```norm
value Point {
  Integer x
  Integer y
}
```

A user-defined value may have methods and generic parameters and may implement interfaces. Its fields cannot be assigned after construction. Assignment, parameter passing, returns, and field reads preserve logical independence, while equality and hash recursively use the language semantics of each field. `value` is contextual only in a top-level declaration header and remains available as an ordinary identifier elsewhere.

## `ref<T>` identifies value storage

`ref<T>` refers to a value storage location. It is not the mechanism for sharing class instances. Use `&location` to take an address and `*reference` to read or write the location. Copying a ref preserves location identity; refs remain within lexical local and call boundaries. The complete rules are in the [`ref<T>` grammar](/en/spec/grammar/references).
