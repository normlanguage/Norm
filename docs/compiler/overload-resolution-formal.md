# Norm Overload Resolution

Norm supports overloads based on name, parameter names and parameter types.

Resolution order:

1. Exact type match.
2. Safe widening conversion.
3. Generic instantiation.
4. Otherwise compilation fails.

The compiler never chooses an arbitrary overload.
