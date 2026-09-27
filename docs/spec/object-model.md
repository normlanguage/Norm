# Object-model specification

Norm composite data consists of identity-free values, identity-bearing classes, behavioral interfaces, and closed enum data types. See [value and identity semantics](/spec/value-identity-semantics) for complete assignment, copying, and equality rules.

## Class

A class may have fields, constructors, and methods. It may extend one class and implement multiple interfaces. Instances are mutable and have stable identity; assignment, argument passing, and return share the same instance. `copy()` creates a new top-level identity and applies ordinary assignment to each field.

Assigning a subclass instance to a superclass or interface variable preserves its dynamic type; no object slicing occurs. Overridable methods dispatch according to the dynamic type.

## Value

A value represents data without identity. Its fields cannot be modified in place after construction; assignment, argument passing, and return produce logically independent values. Equality and hashing recursively use all fields. A value may implement interfaces but does not participate in class inheritance.

## Interface

An interface is the sole nominal behavioral abstraction and stores no instance fields. Implementation and inheritance must be declared explicitly. Interfaces may inherit from multiple interfaces; matching member shapes do not establish a relationship. Interface methods may provide default implementations. Concrete implementations take precedence, and inherited conflicts must be resolved explicitly. Calling through an interface does not change a value's original value or identity category.

## Enum

An enum is a closed algebraic data type. A variant may carry no data or its own distinct fields. Generic enums follow ordinary generic construction and inference rules. Every switch is checked for exhaustiveness, and recursive patterns may destructure variant data. Enums are values.

## `ref<T>`

`ref<T>` gives identity to a value storage location. Copying a ref preserves the same location. It cannot hold a class, because classes already have object identity.

## Representation freedom

The specification does not fix field layout, object headers, garbage collection, or value-copying strategy. The runtime must preserve observable identity, structural equality, dynamic dispatch, and mutation behavior.
