# Complete static-checking pipeline

This page connects name resolution, type checking, flow analysis, and generic solving into an implementable compiler pipeline.

## Stage one: declaration collection

The compiler reads packages and imports, collects all top-level type and function signatures in source roots, and establishes the nominal type graph. This stage detects duplicate names, inheritance cycles, leaks of invisible types, and incorrect arity. It does not depend on file or function-body order.

## Stage two: type-graph validation

1. Resolve `extends` and `implements`.
2. Verify single inheritance for classes and multiple inheritance for interfaces.
3. Check overriding signatures, visibility, and return compatibility.
4. Expand generic bounds and reject invalid cycles.
5. Fix the variant set for each enum.
6. Build reified descriptions of runtime types.

## Stage three: function bodies

Function bodies resolve local names by lexical scope. Every expression receives a static type; every statement updates the definite-assignment set and null state. Return, break, continue, and throw must reach permitted targets.

## Call resolution

Select candidates by name and parameter names, infer generics, apply only allowed safe conversions, then select one uniquely best overload. Overloads distinguishable solely by return type cannot be declared.

## Control flow

Analyze `if` branches separately and intersect their definite-assignment sets at the join. Narrow nullable state from conditions. Treat a `for` body as potentially executing zero times. Check every switch recursively for exhaustive patterns and unreachable cases.

A control expression collects the type of each `break value` and finds one unique common type. A normal path without a value is an error; null is not inserted.

## Value-model checks

- Value fields cannot be written after construction.
- Ordinary class assignment preserves object identity.
- Ordinary value assignment creates a logically independent value.
- `ref<T>` accepts only values and preserves storage-location identity.
- Escape analysis and copy elimination are permitted only when they preserve those results.

## Diagnostic requirements

An error includes a primary source location, related declaration location, actual and expected types, and an actionable explanation. Generic errors show candidate signatures after substitution; flow errors identify a path missing assignment or a result.

## Runtime guarantees

Programs passing static checks retain complete dynamic types and generic arguments. Explicit cast failures, Exceptions, I/O errors, and resource exhaustion may still occur, but unchecked member access and implicit null dereference should not.
