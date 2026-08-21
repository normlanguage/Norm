# Project Status

Norm is currently a language specification draft. The repository defines syntax, semantics, runtime boundaries, and implementation direction; it does not yet contain a usable compiler.

## Specified direction

- Static nominal typing and non-null types by default.
- Value semantics for classes and containers.
- Explicit shared identity through `Ref<T>`.
- Value-producing `if`, `for`, and `switch` expressions using `break value`.
- Reified generic type arguments.
- Garbage-collected execution with a backend-independent typed IR.

## Not available yet

- A production compiler or interpreter.
- A stable standard library.
- Package installation and publishing.
- A supported web application runtime.

The documentation describes intended language behavior. Details may change as the formal specification and compiler prototype develop.

