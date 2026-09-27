# Type inference

Public Norm declarations keep explicit, type-first signatures. Type inference primarily supports generic calls, control-expression results, and local pattern bindings; it does not remove types from public API signatures.

## Inference sites

```norm
List<String> names = emptyList()
String first = identity(value: "Norm")
```

The compiler may infer a function's type arguments from call arguments, the assignment target, and generic constraints. Field and parameter types cannot be omitted. An omitted function return type has fixed semantics determined by its declaration site, not inferred from the body: `Void` at top level and the owner type for class methods.

## Constraint solving

For each type parameter, the compiler collects:

1. Lower bounds or equalities from argument types;
2. Expected types from assignment targets;
3. Upper bounds from `extends` declarations;
4. Additional constraints from nullability and variance rules.

Solving must produce one type that satisfies every upper bound. If ordinary constraints cannot determine trailing parameters that declare default types, their defaults are expanded in declaration order. Other undetermined parameters require explicit type arguments from the caller.

```norm
List<String> names = emptyList<String>()
```

## Inference not performed

- Do not complete public signatures from function bodies.
- Do not search candidates through implicit numeric narrowing.
- Do not infer an arbitrary nullable type from `null` alone.
- Do not guess undeclared structural type relations across modules.

The formal constraints and algorithm appear in [generic inference](/spec/formal/generic-inference).
