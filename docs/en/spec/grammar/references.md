# `ref<T>` references

`ref<T>` represents the identity of a value storage location.

```text
reference type       ::= "ref" "<" type ">"
address expression   ::= "&" addressable-location
read expression      ::= "*" unary-expression
write statement      ::= "*" unary-expression "=" expression
```

Addressable locations are writable locals, parameters, and value-typed fields of class instances. Literals, temporaries, call results, fields of values, container elements, and null-safe member access are not addressable.

The target `T` must be a non-nullable value type. References cannot be nested or nullable. A ref may only be a local or callable parameter type; it cannot occur in a return, field, enum payload, generic argument, function type, or lambda capture.

Copying a ref preserves its location identity. Dereferencing reads an ordinary copy of `T`, writing replaces the stored value, and `==` or `!=` compares locations. The lifetime is bounded by the declaring lexical scope or one callable invocation; 0.10 has no named lifetime syntax.

A line-leading `*reference` starts a new dereference expression; adding `= value` makes it a write statement. It does not continue the preceding semicolon-free expression as multiplication. Keep `*` on the preceding line or use parentheses when multiplication spans lines.

See [Value and Identity Semantics](/en/spec/value-identity-semantics) for the shared assignment and copying rules.
