# Type Syntax

Norm uses a nominal type system and type-first declarations. A type precedes the name of a variable, field, parameter, or return value.

```norm
Integer count = 3
String title = "Grammar"
List<String> names = List<>()
```

## Type forms

```text
Type := NamedType
      | NamedType "<" TypeArgumentList ">"
      | Type "?"
      | FunctionType

TypeArgument := Type | "?"
```

Current forms include named, parameterized, nullable, and function types. Arrays, lists, and maps are generic standard-library types rather than special type syntax.

## Nullable

`T` excludes `null`; only `T?` includes it. A nullable marker applies to the immediately preceding complete type:

```norm
List<String>? optionalList
List<String?> listWithOptionalItems
```

These types differ: the first permits a null list, while the second permits null elements. The reference-type specification defines combinations of `ref<T>` and nullability.

`T?` normalizes after type substitution. If T is already nullable, the result still has one nullable layer. Void cannot be nullable.

The postfix non-null assertion `expression!!` removes the nullable marker from the result type and checks its value at runtime. The operand is evaluated once. If it is null, an exception catchable by `catch Exception` is thrown. The assertion does not change a field's declared type or guarantee non-nullness on later reads.

```norm
Long id = todo.id!!
```

`!!` associates in postfix order like member access, calls, and indexing, before prefix operators. For example, `!checked!!` asserts non-nullness before negation. An already non-null value may be asserted; a null literal without an available non-null type and a Void expression cannot.

## Generic arguments

A generic type in a type annotation must provide all required arguments. Only trailing parameters with declared defaults may be omitted. Norm has no raw types; semantic analysis expands omitted defaults into a complete type. When a constructor call omits type arguments or uses `<>`, arguments and expected type drive inference, then declared defaults fill unresolved parameters. An unresolved required parameter is an error.

```norm
Map<String, Integer> counts
Map counts // compile error
```

Parameterized types are invariant. `?` is an existential projection meaning “an argument exists, but this code does not know which one”:

```norm
Class<?> type
Field<User, ?> field
Function<?> function
```

The read and write rules for projected values are defined in [generic invariance](/spec/generic-variance).

## Function types

```norm
Integer operation(Integer value)
```

A function type contains a return type and parameter list. Parameter names improve local readability but do not affect type equality; the return and every parameter type determine compatibility.
