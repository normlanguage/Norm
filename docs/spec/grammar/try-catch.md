# Try, Catch, and Throw

Exceptions represent execution failures that cannot continue as an ordinary function result. Prefer `Result<T, E>` for expected failures; the exception mechanism does not automatically wrap a Result.

```norm
import std.core.Exception

try {
    loadConfiguration()
} catch IOException error {
    printLine(error.message)
} finally {
    closeResources()
}
```

## Catch selection

Catches match an exception's dynamic type in source order. A more specific type must precede a more general type; a catch entirely covered by an earlier branch is a compile error.

```norm
try {
    readFile()
} catch FileNotFound error {
    printLine(error.path)
} catch IOException error {
    printLine(error.message)
}
```

## Finally

`finally` runs after normal try completion, return, throw, break, or continue. Completion produced by finally replaces the original completion, so it should not contain complex business logic.

## Throw

`throw expression` requires a non-nullable, nongeneric `std.core.Exception` class or subclass as its static type. Catch parameters have the same type boundary. Norm currently has no checked exceptions, and function signatures list no throws set. Libraries should still document exceptions they may throw.

Toolchain runtime errors are outside this nominal type system and are not caught by catch.

Resource types should prefer the standard library's scoped-cleanup abstraction. Until that API is finalized, examples in this specification use explicit `try/finally`.
