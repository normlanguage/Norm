# Value and Identity Semantics

This page is the normative source for assignment, parameter passing, returns, equality, and copying in Norm. The object model, containers, and reference syntax follow these rules.

## Data categories

Norm distinguishes value from identity:

| Category | Types | Assignment, parameters, and returns | `==` |
| --- | --- | --- | --- |
| Value | `Integer`, `Boolean`, `String`, enums, user-defined values, built-in containers | Produces a logically independent value | Structural equality |
| Identity | `class` instances | Copies an object reference and shares the object | Object identity |

The `=` operator always copies the value of its right-hand expression. A class variable's value is an object reference, while a container's value is its container structure.

```norm
Box first = Box(value: 1)
Box second = first
second.value = 2
printLine(first.value)
```

This prints `2`.

## Classes and explicit copying

Assignment, parameter passing, and returns preserve a class instance's identity. Every class provides `copy()`, which creates a new top-level object and assigns each field using the ordinary rules. Value fields become logically independent; class fields still refer to the same nested objects. Norm does not perform implicit recursive object cloning.

```norm
Box second = first.copy()
```

## Containers

`Array`, `List`, `Map`, `Set`, `Stack`, `Queue`, `Deque`, `Pair`, `Range`, and `StringBuilder` are values. Copying one creates an independent structure and assigns each element according to its category. Value elements thus become logically independent, while class elements keep their identity. Containers use one recursive equality-and-hash rule, including for Map keys and Set elements. `Equatable` and `Hashable` are explicit domain protocols and do not override this built-in key semantics.

An indexed result used directly to mutate a container retains its location in that container: `rows[0].add(1)` changes the nested list. By contrast, `var row = rows[0]` copies the list under assignment rules, so subsequent changes to `row` do not affect `rows`. Passing or returning an indexed result follows the same copying rules.

## Evaluation and calls

Argument expressions are evaluated from left to right in source order. Labels only select parameter slots and never reorder evaluation. Multi-parameter calls use `name: value`; a bare identifier is shorthand only when it matches the parameter at the same position. A single argument may omit its label.

```norm
merge(left: mergeSort(left), right: mergeSort(right))
```

## Implementation freedom

Logical independence does not require immediate deep copying. An executor may use copy-on-write, structural sharing, escape analysis, or copy elimination, provided identity, equality, mutation results, and source evaluation order remain unchanged.

## `ref<T>`

`ref<T>` represents the identity of a value storage location. It is not required for class sharing and does not accept class types. `&location` takes an address, `*reference` reads the stored value, and `*reference = value` writes it. Ref equality compares location identity. Refs are confined to local variables and callable parameters, cannot escape through returns, fields, containers, generic arguments, function types, or lambda capture, and use lexical scope or a single call as their lifetime boundary. The normative rules are in the [`ref<T>` grammar](/spec/grammar/references).
