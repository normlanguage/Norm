# Generic invariance

Norm parameterized types are invariant. `List<Circle>` and `List<Shape>` are distinct types and cannot be assigned directly to each other; nullability does not change this rule.

The existential projection `?` hides one existing type argument. Reading a method result that is the hidden parameter uses its declared upper bound, or `Any?` when unconstrained, and preserves declared nullability. Nested arguments remain projected: reading `List<T>` produces `List<?>`, not `List<Any?>`.
