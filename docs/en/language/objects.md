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

## Containers are values

Copying a built-in container creates an independent structure. Class elements within that structure retain their identity. Values compare structurally; classes compare by identity.

## `ref<T>` identifies value storage

`ref<T>` refers to a value storage location. It is not the mechanism for sharing class instances. Copying a ref preserves the location identity; its expression forms are defined by the grammar specification.
