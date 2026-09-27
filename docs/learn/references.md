# 10 References

`ref<T>` expresses the identity of a value storage location, making mutable aliasing explicit and limiting it to a lexical lifetime.

<<< ../../norm/tests/docs/tour/10_references.norm{norm}

Output:

```text
2
```

| Form | Meaning |
| --- | --- |
| `ref<T>` | A reference to a storage location for `T` |
| `&location` | Obtain the address of a writable location |
| `*reference` | Read the value in that location |
| `*reference = value` | Replace the value in that location |

## Addressable locations

Writable local variables, parameters, and value fields of a class can be addressed. Literals, temporary expressions, call results, value fields, container elements, and results of null-safe access cannot.

## Type and lifetime boundaries

- `T` must be a value type.
- A ref cannot be nested or nullable.
- Refs are limited to local variables and callable parameters.
- A ref cannot be a return type, field, enum payload, generic argument, or part of a function type.
- A lambda cannot capture a ref, and a ref cannot outlive its referenced location.

Classes already have object identity; `ref<Class>` is not used to share them. See the [`ref<T>` reference](/spec/grammar/references) for complete static rules.

Previous: [Errors and exceptions](/learn/errors). Next: [Annotation](/learn/annotations).
