# Norm Dependency Injection Design

Norm does not require heavy runtime magic.

Dependency graphs should be explicit.

Example:

```norm
UserService service = UserService(
    repository = repository.ref()
)
```

Annotations may provide registration metadata, but construction rules remain visible.

The goal is predictable application architecture.
