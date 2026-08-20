# Norm Object Runtime

## Object Representation

Norm runtime separates:

- value storage
- object identity
- type metadata

## Class Runtime

A class instance contains:

- fields
- runtime type information
- method dispatch information

## Value Runtime

Values do not expose identity.

The runtime may use:

- copy-on-write
- immutable sharing
- scalar replacement

## Interface Dispatch

Interface calls use runtime metadata to locate implementations.

## Reflection

Reflection metadata is retained because Norm supports:

- reified generics
- annotations
- runtime inspection

## Ref Runtime

Ref<T> is an explicit managed shared reference.

It is the only normal way to create shared mutable identity.
