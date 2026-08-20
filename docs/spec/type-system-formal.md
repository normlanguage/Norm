# Norm Type System Formal Specification

## Overview

Norm type system is designed around four goals:

1. Static verification before execution.
2. Predictable runtime behavior.
3. Strong support for application development.
4. Complete runtime type information.

Norm rejects implicit dynamic conversion. Every value has a known type.

## Type Categories

Norm contains:

- Primitive types
- Value types
- Class types
- Interface types
- Enum types
- Function types
- Generic types

## Nullability

Every reference-like type is non-null by default.

```norm
String name = "Alice"
String? nickname = null
```

The compiler performs definite assignment analysis. A non-null value must be initialized on every construction path.

## Nominal Typing

Norm uses nominal typing.

A type relationship exists only when explicitly declared.

```norm
interface Serializable {
    String serialize()
}

class User implements Serializable {
}
```

A class with the same method shape is not automatically compatible.

## Generic Types

Generics preserve runtime metadata.

```norm
List&lt;String&gt;.class
```

contains:

```
raw type: List
type argument: String
```

This enables reflection, serialization and dependency injection.

## Value Semantics

Assignment follows language semantics, not physical implementation.

```norm
User b = a
```

means an independent value copy.

The runtime may optimize using copy-on-write.

## References

Shared identity requires explicit reference creation.

```norm
Ref&lt;User&gt; shared = user.ref()
```

The compiler can distinguish local values from shared mutable state.

