# Interface Declarations

An interface is Norm's only nominal abstraction for behavior and holds no instance fields. A standard-library “protocol” is an ordinary interface serving a general protocol role, not another kind of declaration or matching mechanism.

```norm
interface Formatter<T> {
    String format(T value)
}

class PointFormatter implements Formatter<Point> {
    String format(Point value) {
        return "point"
    }
}
```

## Rules

- A type satisfies an interface only by explicitly writing `implements`; same-named methods do not match structurally.
- An interface may `extends` several interfaces, but the inheritance graph cannot contain a cycle.
- Implementation parameter types, return types, and visibility must satisfy the contract.
- An interface does not change data category: a class passed through an interface retains object identity, while a value retains value semantics.

```norm
interface Ordered<T> extends Comparable<T>, Equatable<T> {
}
```

An interface method may declare a signature or provide a body. If a concrete type does not override it, the one applicable default implementation is used; conflicting inherited defaults must be resolved explicitly by the concrete type.

The runtime check `value is InterfaceName` uses declared relationships, not member shape.
