# Norm Object Runtime Model

## Object Identity

Norm separates value and identity.

A normal assignment:

```norm
User b = a
```

means value copy semantics.

Shared identity requires:

```norm
Ref<User> b = a.ref()
```

## Runtime Metadata

Objects contain metadata for:

- runtime type
- generic arguments
- methods
- annotations
- GC tracking

## Method Dispatch

Class methods use dynamic dispatch when overridden.

Private methods are statically bound to their declaring class.

Interface dispatch uses interface tables.
