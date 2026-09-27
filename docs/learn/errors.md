# 09 Errors and exceptions

Public, expected failures belong in result types. Failures that interrupt normal execution use exceptions.

<<< ../../norm/tests/docs/tour/09_errors.norm{norm}

Output:

```text
empty input
```

## Expected failures

Business outcomes use ordinary data enums and exhaustive switches. The standard-library `Result<T, E = String>` follows the same construction and pattern rules: use `Result<T>` and `Result.Err("message")` for a textual reason, or specify `E` for typed categories. Success without a business value uses `Result<Unit>` or `Result<Unit, E>`.

Norm has no special operator for automatically propagating a Result. Ordinary `return`, `switch`, and calls still make every exit explicit.

## Exceptions

Exceptional control flow uses `try`, `catch`, `finally`, and `throw`:

```norm
try {
  readConfiguration()
} catch IOException error {
  printLine(error.message)
} finally {
  closeResources()
}
```

A missing lookup normally uses a nullable type; a finite normal outcome uses an enum; an I/O interruption or failed internal invariant uses a typed exception. Standard-library pages list the concrete failure types at each boundary.

See the [error model](/spec/error-model) and [try/catch reference](/spec/grammar/try-catch) for exception selection and completion rules for `finally`.

Previous: [Lambda and Extension](/learn/lambdas-extensions). Next: [References](/learn/references).
