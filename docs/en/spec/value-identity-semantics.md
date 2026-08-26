# Value and Identity Semantics

This page is the normative source for assignment, parameter passing, returns, equality, and copying in Norm.

## Categories

| Category | Types | Assignment, parameters, and returns | `==` |
| --- | --- | --- | --- |
| Value | `Integer`, `Boolean`, `String`, enums, built-in containers | Produces a logically independent value | Structural equality |
| Identity | `class` instances | Copies an object reference and shares the object | Object identity |

The `=` operator always copies the value of its right-hand expression. A class variable's value is an object reference, while a container's value is its container structure.

## Class copying

Assignment, parameter passing, and returns preserve a class instance's identity. Every class provides `copy()`, which creates a new top-level object and assigns each field using the ordinary rules. Value fields become logically independent; class fields still refer to the same nested objects. Norm does not perform implicit recursive object cloning.

## Containers

Built-in containers are values. Copying one creates an independent structure and assigns each element according to its category. Class elements therefore keep their identity. Containers use one recursive equality-and-hash rule, including for Map keys and Set elements. `Equatable` and `Hashable` are explicit domain protocols and do not override this built-in key semantics.

## Calls

Argument expressions are evaluated from left to right in source order. Labels only select parameter slots and never reorder evaluation. Multi-parameter calls use `name: value`; a bare identifier is shorthand only when it matches the parameter at the same position. A single argument may omit its label.

## `ref<T>`

`ref<T>` represents the identity of a value storage location. It is not required for class sharing and does not accept class types. Addressing, dereference, and equality syntax remain to be fixed by the grammar specification.
