# Value Declaration Syntax

A `value` declaration defines an immutable data type without identity. It suits coordinates, ranges, identifiers, and other data where equal content means equality.

```norm
value Point {
    Integer x
    Integer y
}

Point origin = Point(x: 0, y: 0)
```

## Static rules

A value may declare constructors and constructor overloads; without one it uses field construction. A constructor may initialize fields of its current object, but may not modify other values or carry mutation permission into a lambda. All normally exiting constructor paths must finish field initialization.

```norm
value Row<T> {
    List<T> children
    Integer spacing

    Row(List<T> children) {
        this.children = children
        spacing = 0
    }

    Row(List<T> children, Integer spacing) {
        this.children = children
        this.spacing = spacing
    }
}
```

- Every field must have a non-null type or explicitly be declared nullable.
- Every field must be initialized before construction ends.
- Fields cannot be assigned in place after construction.
- A `value` cannot inherit a class or be inherited by a class; it may implement an interface.
- Equality and hash derive recursively from every field.

```norm
origin.x = 1 // compile error: value fields cannot be modified
origin = Point(x: 1, y: 0) // valid: variable binds to a new value
```

Copying a `value` produces logically independent results. The compiler may share structure as long as the program cannot observe shared identity.

## Boundary with Class

Use `value` when methods are needed but identity is not. Use `class` when object identity and internal mutable state are needed. `ref<T>` points to value storage locations and is not used for class sharing.
