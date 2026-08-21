# Reified Generics

Norm generics preserve actual type arguments at runtime. Generic syntax supports safe reuse without becoming a separate type-level programming language.

## Generic types

```norm
class Box<T> {
    T value
}

Box<int> count = Box<int>(value = 3)
Box<String> label = Box<String>(value = "ready")
```

Raw types are forbidden: `Box` without a type argument is invalid.

## Constraints

```norm
T maximum<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(right) >= 0 {
        return left
    }
    return right
}
```

The constraint requires `T` to explicitly implement `Comparable<T>` under Norm's nominal type system.

## Runtime type information

```norm
List<String>.class
List<int>.class
List<String>.class.T == String.class
```

`List<String>` and `List<int>` have distinct runtime type descriptions. Reflection and generic libraries do not need a separate `Class<T>` token to recover information the source already contains.

## Use-site variance

```norm
List<? extends Shape> shapes
List<? super Circle> destinations
```

Norm uses explicit Java-style use-site variance and does not permit arbitrary compile-time type computation.

Return to the [handbook introduction](/en/language/overview).

