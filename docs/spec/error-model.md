# Error model

Norm distinguishes failure values within a normal contract from Exceptions that interrupt normal evaluation.

## Result

`std.core.Result<T, E = String>` is an ordinary generic enum defined by the standard library. An application domain uses it to return mutually exclusive outcomes explicitly through a function contract. A switch handles it explicitly; the language has no hidden automatic-propagation operator.

Both Ok and Err carry a `String msg` whose default is the empty string. A simple failure uses `Result<T>` and `Result.Err("message")`, with `String` as the default error type. For programmatic classification, use `Result<T, Failure>` and `Result.Err(Failure.Invalid, msg: "message")`. `msg` supplies information for users or boundaries; it does not replace an explicit error type.

```norm
String message = switch reserve(command: command) {
    case Ok(Reservation value) { break value.id }
    case Err(_, String msg) { break msg }
}
```

```norm
Result<String> simple = Result.Err("Name is required")
Result<String, ReservationFailure> typed =
    Result.Err(ReservationFailure.Unavailable, msg: "No seats remain")
```

## Ordinary absence

Use a nullable return type when a lookup misses and error details are unnecessary. The application domain decides whether Result belongs in a contract; it cannot bypass the system-level exception boundary.

## Exception

`std.core.Exception` is the nominal class root of all throwable exceptions. Exception subclasses use ordinary single inheritance. A catch accepts only a non-nullable, non-generic Exception subtype. `throw`, `try`, `catch`, and `finally` handle exceptions that cannot continue as the current function's normal result. Norm has no checked exceptions and does not automatically turn `Result.Err` into an Exception.

Failures in the current system-facing I/O, filesystem, HTTP, and time operations all throw domain Exceptions instead of returning Result. See the [standard-library overview](/stdlib/overview) for the delivered scope.

Toolchain faults such as runtime argument errors, out-of-bounds access, and division by zero use stable runtime error codes. They are outside the user Exception hierarchy and cannot be intercepted by catch.

## Boundary conversion

A transport layer may map a domain Result to an HTTP status, and a CLI may map it to an exit code. Mapping occurs in boundary functions, keeping core business types independent of transport protocols.

A return, throw, break, or continue produced by `finally` replaces the original completion. Libraries should use scoped-resource APIs where possible to reduce ambiguity from cleanup failures.
