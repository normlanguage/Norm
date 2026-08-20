# Norm Memory Model

## Design Goal

Norm separates language semantics from runtime implementation.

The language provides:

- value semantics by default
- explicit shared references
- garbage collection

## Value Semantics

Assignment represents logical copying.

The runtime may optimize with:

- copy-on-write
- escape analysis
- copy elimination

## Ref

`Ref<T>` explicitly introduces shared identity.

```norm
Ref<User> user = original.ref()
```

## Garbage Collection

The first runtime implementation may use a mature tracing GC through the selected runtime platform.

Future runtimes can evolve without changing language semantics.
