# Generics

Generics let one type or function work with multiple types while preserving static type information.

## Current boundary

The current implementation supports generic classes, top-level generic functions, parameterized core collections, nested nullable type arguments, inference from arguments and expected return types, and runtime type arguments. Generic types are invariant and raw types are invalid.

Bounds, interface constraints, use-site variance, generic data enums, generic methods, and reflection APIs are later strict extensions.

## Generic types

```norm
class Box<T> {
    T value
}

Box<Integer> count = Box<Integer>(value: 3)
Box<String?> label = Box<String?>(value: null)
```

Raw types are forbidden: `Box` without a type argument is invalid.

## Generic functions

```norm
T identity<T>(T value) {
    return value
}

Integer count = identity(3)
String? label = identity(null)
```

The expected `String?` return type supplies the type information that `null` cannot provide by itself.

## Invariance

Different type arguments produce different invariant types. `List<String>` is not assignable to `List<String?>`; nullable elements must be declared at the collection boundary.

Return to the [handbook introduction](/en/language/overview).
