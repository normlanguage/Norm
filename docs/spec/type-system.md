# Norm Type System Specification

## Overview

Norm uses a static nominal type system designed for application software. The type system favors explicit relationships and predictable behavior.

Core goals:

- avoid implicit conversions
- preserve runtime type information
- make business models easy to express
- allow aggressive compiler optimization

## Type Categories

Norm has several fundamental type categories:

- primitive types
- class types
- value types
- interface types
- enum types
- function types

There is no universal Object root type.

## Null Safety

Nullable types are explicitly marked with `?`.

```norm
String name = "Alice"
String? nickname = null
```

The compiler performs definite assignment analysis before accepting non-null variables.

## Nominal Typing

A type relationship must be explicitly declared.

```norm
class User implements Serializable {
}
```

Structural compatibility is not enough.

## Generic Design

Generics preserve runtime metadata.

```norm
List&lt;String&gt;.class
```

can expose its type argument information.

