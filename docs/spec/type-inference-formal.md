# Formal type-inference rules

Type inference is a constrained solving process. It fills in only information uniquely determined at the call site and never infers public types from function bodies. An omitted function return type is determined directly by the declaration site and does not enter constraint solving.

## Sources of constraints

The compiler establishes an independent solving session for each candidate and collects:

- Assignability constraints from arguments to parameters;
- Expected types from assignment targets or return contexts;
- Declared `extends` upper bounds of type parameters;
- Type relations arising from nullability, interface constraints, and nominal protocol projections;
- Target container and element types for sequence literals;
- Owner type parameters for diamond constructors;
- Representability constraints on concrete numeric leaf types from numeric literals.

## Solving

1. Resolve names and overload candidates.
2. Propagate expected types into expressions from assignment, return, or argument positions.
3. Collect constraints in the reverse direction from expressions' own types.
4. Combine equality, subtype, nullable, and nominal-conformance constraints.
5. Solve generic parameters and instantiate nested expressions.
6. Finally determine concrete numeric-literal types.
7. Validate every argument and select one uniquely best candidate.

```norm
T identity<T>(T value) { return value }
String name = identity(value: "Norm")
```

This yields the constraint `T = String`.

```norm
List<Pair<Integer, String>> values = List<>()
values.add(Pair<>(first: 7, second: "seven"))
```

The outer collection element type propagates into `Pair<>`, yielding `A = Integer` and `B = String`. The literal `7` is materialized as `Integer` after solving. Inference propagates only along explicit type relations; it does not search for nearby type names.

## Rejection conditions

The compiler does not use implicit numeric narrowing, arbitrary union types, function-body analysis, or runtime values to complete inference. `null` has no independent concrete type and cannot be inferred without an expected nullable type. Empty `[]` and parameterless `List<>()` likewise require an external constraint. Failure diagnostics list unresolved type variables and conflicting constraints.
