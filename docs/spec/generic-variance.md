# Generic invariance

Norm parameterized types are invariant. `List<Circle>` and `List<Shape>` are distinct types and cannot be assigned directly to each other; nullability does not change this rule.

General read and write capabilities are expressed through explicit interfaces and generic constraints. The current type syntax has no use-site wildcards.
