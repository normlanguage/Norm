# `ref<T>` Reference Syntax

`ref<T>` expresses the identity of a value storage location. It does not provide class sharing; class instances already have identity.

## Syntax

```text
Reference type   ::= "ref" "<" Type ">"
Address expression ::= "&" AddressableLocation
Read expression  ::= "*" UnaryExpression
Write statement  ::= "*" UnaryExpression "=" Expression
```

`&` and `*` are unary operators. Reading produces an ordinary `T` value, while writing replaces the value at the target location. Copying `ref<T>` preserves location identity; two refs of the same type compare location identity with `==` and `!=`.

A line-initial `*reference` starts a new dereference expression. With `= value` it forms a write statement rather than multiplication with the previous line's expression that omitted a semicolon. For multiplication across lines, leave `*` on the previous line or parenthesize to make the expression boundary clear.

## Addressable locations

Only writable local variables, parameters, and value fields of classes are addressable. Literals, temporary expressions, call results, fields of values, container elements, and null-safe member access are not. Once a location is addressed, the executor must preserve stable identity during its lexical lifetime.

## Type boundary

- `T` must be a value type.
- `ref<Class>` is invalid.
- `ref<ref<T>>` and nullable refs are invalid.
- A ref is allowed only as a local-variable type or callable-parameter type.
- A ref cannot appear as a return type, field, enum payload, generic argument, or part of a function type.

## Lifetime

Version 0.10 introduces neither named lifetimes nor lifetime annotations. A ref's lifetime is bounded by the lexical scope of its local declaration or one callable invocation. A ref can be copied, reassigned, or passed to a ref parameter, but cannot be returned, stored in an object or container, or captured by a lambda. These restrictions prevent any ref from outliving its storage location.

See [Value and Identity Semantics](/spec/value-identity-semantics) for established value, class, and container rules.
