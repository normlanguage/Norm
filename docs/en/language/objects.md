# Value Semantics and Ref

Norm treats identity and sharing as part of the visible type model. `class`, `value`, and `Ref<T>` answer different questions.

## Class values are independent by default

```norm
class Counter {
    int value

    void increment() {
        value = value + 1
    }
}

Counter first = Counter(value = 0)
Counter second = first
second.increment()
```

After this code, `first.value` is `0` and `second.value` is `1`. Assignment has value semantics even though `Counter` is mutable and has behavior.

An implementation may use copy-on-write, structural sharing, or copy elision. Those optimizations cannot change observable behavior.

## Value declares immutable data

```norm
value Point {
    int x
    int y
}
```

A value has no identity and its fields cannot be mutated in place. The variable itself may be assigned a different complete value.

## Ref introduces shared identity

```norm
Counter counter = Counter(value = 0)
Ref<Counter> shared = counter.ref()
shared.increment()
```

`Ref<Counter>` tells the reader that aliases can observe changes to the same object. Sharing cannot be introduced by ordinary assignment.

`Ref<T>` is never nullable. Both `Ref<Counter>?` and `Ref<Counter?>` are invalid; absence should be modeled explicitly instead of mixing nullability with identity.

## Choosing a model

| Requirement | Type form |
| --- | --- |
| Immutable data with no identity | `value` |
| Mutable behavior with independent assignment | `class` |
| Shared mutable identity | `Ref<class>` |
| A behavior contract | `interface` |

Next: [Control-flow expressions](/en/language/control-flow).

