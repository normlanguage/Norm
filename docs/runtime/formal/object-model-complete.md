# Norm Runtime Object Model

## 1. Runtime Object Categories

Norm runtime manages:

- Value storage
- Managed objects
- References
- Metadata objects

## 2. Object Header

A managed object contains runtime metadata such as:

- type information
- GC information
- synchronization state
- reflection metadata pointer

## 3. Method Dispatch

Class methods use runtime dispatch tables.

Interface calls use interface metadata tables.

Private methods are statically bound.

Public instance methods participate in dynamic dispatch.

## 4. Reflection

Reflection is based on runtime metadata rather than source rewriting.

The runtime can expose:

- class information
- fields
- methods
- annotations
- generic arguments

## 5. Garbage Collection

GC manages shared runtime objects.

Value semantics reduce unnecessary aliasing, while Ref<T> explicitly creates shared graphs.
