# Enum Declarations

An enum declares a closed set of variants. Variants may be empty or carry typed data.

```norm
enum ParseResult {
    Success(Integer value),
    Empty,
    Invalid(String reason, Integer position)
}
```

Variant parameters put types first. Construct a variant with named arguments:

```norm
ParseResult result = ParseResult.Invalid(
    reason: "unexpected character",
    position: 3
)
```

## Restrictions

- Variant names are unique in the enum's construction namespace.
- Variant data obey ordinary definite-assignment rules for fields.
- An enum cannot be inherited or extended with variants in another file.
- A generic enum declares type parameters after the enum name.

```norm
enum Outcome<T, E = String> {
    Success(T value),
    Failure(E error)
}
```

Variant construction is a call on an enum type, following ordinary rules for explicit and inferred generic arguments. Using the standard-library Result:

```norm
Result<Integer, Error> explicit = Result<Integer, Error>.Ok(value: 1)
Result<Integer> defaultError = Result.Err("invalid")
Result<Integer, Error> inferred = Result.Ok(value: 1)
```

Variant parameters support ordinary defaults. When there is one required primary parameter and all others have defaults, the primary parameter may keep the single-argument call form:

```norm
enum Outcome<T> {
    Success(T value, String msg = "")
}

Outcome<Integer> result = Outcome.Success(1)
```

When enum type arguments are omitted, actual arguments and expected type participate in ordinary inference first. Unsolved trailing parameters with declared defaults then use those defaults; other unresolved parameters require an explicit form. Switch patterns destructure variant data. See [pattern matching](/spec/grammar/patterns) and [Switch](/spec/grammar/switch) for patterns and exhaustiveness.
