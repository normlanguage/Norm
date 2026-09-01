# Generics

Generics let one type or function work with multiple types while preserving static type information.

## Current boundary

The current implementation supports generic classes, data enums, functions and instance methods, nominal class and interface bounds, bounds referencing earlier type parameters, parameterized core collections, nested nullable type arguments, inference from arguments and expected return types, and runtime type arguments. Generic types are invariant and raw types are invalid. Use-site variance and reflection APIs remain later extensions.

## Generic types

```norm
class Box<T> {
    T value
}

Box<Integer> count = Box<>(value: 3)
Box<String?> label = Box<>(value: null)
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

Instance methods use the same inference and explicit type-argument syntax. A generic class's type arguments precede method type arguments in the runtime type environment:

```norm
class Values<T> {
    Pair<T, U> pair<U>(T first, U second) {
        return Pair<>(first: first, second: second)
    }
}

Pair<String, Integer> value = Values<String>().pair<Integer>(first: "Norm", second: 4)
```

## Invariance

Different type arguments produce different invariant types. `List<String>` is not assignable to `List<String?>`; nullable elements must be declared at the collection boundary.

## Nominal bounds

```norm
T larger<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(other: right) >= 0 { return left }
    return right
}
```

A bound names one non-null class, interface, or earlier type parameter. A class bound is satisfied through class inheritance. An interface bound is satisfied through an explicit `implements` or interface `extends` relationship. A type-parameter bound is checked after substituting the enclosing type arguments, as in `U extends T`. Matching member names are not sufficient. Calls and bound method values through a class bound retain virtual method dispatch; calls through an interface bound use interface dispatch.

Return to the [handbook introduction](/en/language/overview).
