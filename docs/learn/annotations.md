# 11 Annotation

Annotations are declaration objects with types, targets, and retention policies. Ordinary nominal interfaces still constrain their metadata and optional behavior.

<<< ../../norm/tests/docs/tour/11_annotations.norm{norm}

Output:

```text
coordinate
```

## Targets and retention

An annotation must implement at least one target interface and choose a retention policy. `TypeTarget`, `FieldTarget`, `FunctionTarget`, and `ParameterTarget` determine where it can be applied; `SourceRetention`, `BinaryRetention`, and `RuntimeRetention` determine how long it is retained.

Application arguments must be named and use compatible compile-time scalars, declaration references, or `List` constants. Non-nullable parameters are required; nullable ones may be omitted. The same annotation type cannot be applied twice to one target.

## Typed behavior

Advanced annotations can implement:

- `FunctionInterceptor`;
- `ParameterInterceptor<T>`;
- `FieldInterceptor<T>`.

Their lifecycle uses `before`, `around`, and `after`. Parameter and field interceptor type arguments must exactly match the declared type. Direct calls, dynamic dispatch, and function references share definition-side behavior.

Reflection reads runtime metadata through `T.class`, `Class<T>`, and strongly typed declaration references, not JVM reflection or string getters. Built-in `@Document` stores text in `description` and associates declarations through its `types`, `functions`, and `fields` lists. See the [annotation specification](/spec/annotations) for the full lifecycle and [declaration references and reflection](/spec/declaration-references) for reference rules.

Previous: [References](/learn/references). Next: [Package and Module](/learn/packages-modules).
