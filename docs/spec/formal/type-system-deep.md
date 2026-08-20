# Norm Formal Type System

## Overview

Norm uses a nominal, statically checked type system designed for application software. The type system prioritizes predictable behavior over maximum expressiveness.

## Type Categories

Norm has several fundamental type categories:

- Primitive types: int, long, float, double, decimal, boolean, String
- Value types: declared with `value`
- Object types: declared with `class`
- Contract types: declared with `interface`
- Algebraic types: declared with `enum`
- Function types
- Nullable types: T?
- Reference types: Ref<T>

## Nullable Rules

A non-null type can never contain null.

```norm
String name = "Norm"
String? nickname = null
```

The compiler performs flow analysis to prove nullable safety.

## Generic Rules

Generics preserve runtime information.

```norm
List<String>.class
```

The runtime can inspect the generic argument.

Unlike Java erasure, Norm keeps:

- generic declaration metadata
- actual type arguments
- reflection information

## Variance

Variance is explicit and follows Java-style use-site variance.

```norm
List<? extends User>
List<? super Employee>
```

The compiler rejects unsafe substitutions.
