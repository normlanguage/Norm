# Norm Object Model Formal Design

## Object Categories

Norm separates:

- class
- value
- interface
- Ref<T>

## Class

Class provides behavior and inheritance.

```norm
class User {
    String name
}
```

Classes have runtime metadata and support polymorphism.

## Value

Values represent pure data.

```norm
value Money {
    decimal amount
}
```

Values do not have shared identity.

## Interface

Interfaces contain behavior contracts only.

They cannot contain instance fields.

## Method Dispatch

Public methods participate in dynamic dispatch.
Private methods belong only to their declaring type.

## Copy Semantics

Logical copy is recursive.

Runtime optimizations may use:

- copy-on-write
- escape analysis
- scalar replacement

## Reflection

Runtime metadata includes:

- type name
- fields
- methods
- generic parameters
- annotations
