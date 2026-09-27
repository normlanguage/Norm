# Formal generic system

Norm generics provide static reuse at compile time and retain actual type arguments at runtime. They do not form a separate type-level programming language.

## Declarations

```norm
class Box<T> {
    T value
}

T maximum<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(other: right) >= 0 { return left }
    return right
}
```

A type variable is visible within its declaration body and member signatures. Operations using it must be valid for every actual type satisfying its bound.

## Instantiation

`G<A1...An>` requires every mandatory argument, each satisfying its corresponding bound. Trailing parameters with declared default types may be omitted. Semantic analysis substitutes earlier arguments in order and expands them into a complete argument list. Raw `G` is invalid. Distinct actual arguments produce incompatible invariant types by default.

## Type inference

Function calls derive constraints from arguments, expected return types, and declared bounds. After ordinary constraint solving, unsolved trailing parameters with declared defaults use those defaults. Other unsolved parameters require explicit type arguments.

## Runtime representation

Each parameterized type description contains at least:

- Generic declaration identity;
- Ordered actual type arguments;
- Nullability information;
- Bounds and substituted member results.

Declared defaults are part of the public ABI. An instance's runtime description stores only the expanded, complete actual arguments.

This information enters Core IR and the runtime type environment directly, without an extra Class token.

## Binaries and caches

An implementation may share generic machine code, monomorphize, or use a hybrid strategy, but its runtime type descriptor must remain complete. Compilation cache keys contain the generic declaration version and actual arguments; code sharing cannot incorrectly reuse an incompatible layout.

## Limits

Current Norm has no higher-kinded types, type functions, conditional types, use-site wildcards, or implicit typeclass search.
