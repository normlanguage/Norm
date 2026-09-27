# Validation API

`std.validation` provides strongly typed annotation constraints for function parameters and object fields. Parameters are checked before entering the function body; fields are checked before initialization, assignment, and `ref<T>` writes. Failure throws `ConstraintViolation`, and the corresponding write is not committed.

```norm
import std.validation.CodePointSize
import std.validation.Min
import std.validation.NotBlank

class Account {
  @NotBlank()
  String owner

  @Min(value: 0)
  Integer balance
}

Void register(@CodePointSize(minimum: 3, maximum: 32) String name) {
}
```

## Public contract

All built-in constraints implement `Constraint<T>`. It combines `ParameterInterceptor<T>`, `FieldInterceptor<T>`, and `SourceRetention`, and defines a constraint through `isValid(T)`, a stable `code()`, and a display `message()`. A user annotation implementing the same interface reuses the same lifecycle and failure model.

`ConstraintViolation` provides:

- `location`: `ConstraintLocation.Parameter` or `ConstraintLocation.Field`;
- `functionReference`: the `Function<?>` owning a parameter constraint;
- `parameterReference`: the `Parameter<?>` for a parameter constraint;
- `fieldReference`: the `Field<?, ?>` for a field constraint;
- `code`: a stable machine identifier;
- `message`: the default display text inherited from `Exception`.

`location` determines which declaration references are non-null: a Parameter location provides the function and parameter, while a Field location provides the field. Names and types are queried through the references instead of being stored again in the exception.

[`validation/constraints.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/validation/constraints.norm) defines the implementation and complete declarations.

## Built-in constraints

| Type | Annotations |
| --- | --- |
| `Boolean` | `AssertTrue`, `AssertFalse` |
| `Integer` | `Min`, `Max`, `Negative`, `NegativeOrZero`, `Positive`, `PositiveOrZero` |
| `String` | `NotEmpty`, `NotBlank`, `CodePointSize`, `GraphemeSize` |

`CodePointSize` and `GraphemeSize` distinguish Unicode code-point and grapheme-cluster length. Default non-null `T` and explicitly nullable `T?` types express nullability constraints.

`CodePointSize` and `GraphemeSize` require `0 <= minimum <= maximum`. An invalid definition throws `ConstraintDefinitionException` when the annotation is first executed. Its `code` provides a stable machine identifier for the corresponding constraint.

## Custom constraints

```norm
import std.validation.Constraint

annotation Even implements Constraint<Integer> {
  public Boolean isValid(Integer value) {
    return value % 2 == 0
  }

  public String code() {
    return "even"
  }

  public String message() {
    return "must be even"
  }
}
```

Multiple constraints run in annotation source order and throw on the first failure. A parser that must collect several input errors should form its own structured result before constructing the domain object.
