# Type-system specification

Norm has a static, nominal, non-null-by-default type system. Before execution, the compiler resolves every expression's type and rejects programs that depend on unsafe implicit conversions.

## Type categories

- Top type: `Any`;
- Primitive types: `Boolean`, `Integer`, `Long`, `Float`, `Double`, `Number`, `CodePoint`, `String`;
- User types: classes, interfaces, and data enums;
- Parameterized types: `List<T>`, `Map<K, V>`, and others;
- Nullable types: `T?`;
- Function types: `Function<R(P...)>`;
- Existential type projections: `?` in a type-argument position.

`Any` is the static top type of all non-null values; `Any?` additionally includes null. Concrete values may be promoted safely to `Any`. Conversion in the reverse direction is not implicit, and `Any` does not permit direct calls to members of a concrete type. Use an interface or constrained generic for general behavior.

## Nominal relations

Only a class's `implements` and an interface's `extends` establish nominal relationships. Identical fields or methods do not make types automatically compatible.

## Nullability

`String` excludes null; `String?` includes it. A nullable value must be narrowed by a check before assignment to a non-null location:

```norm
String? input = readInput()
if input != null {
    printLine(input)
}
```

Narrowing remains valid only while no path can modify the variable.

## Assignability

The main cases in which `S` is assignable to `T` are: `T` is `Any` with nullability covering `S`; `S` and `T` are the same; `S` explicitly satisfies the interface `T`; `S` is the non-null part of `T?`; or `S` is a concrete numeric leaf type and `T` is `Number`. There is no implicit non-literal conversion between distinct numeric leaf types. Parameterized types remain invariant.

Collection-literal element types use the most precise common type. If concrete element types differ but all satisfy `Stringable`, that interface may be their common type; lacking a common type is still a type error.

## Value and identity

Primitive types, enums, and built-in containers follow value-assignment rules; class assignment preserves object identity. See [value and identity semantics](/spec/value-identity-semantics) for the complete rules.

## Generics

Generics are invariant, have no raw types, and retain actual type arguments at runtime. Inference must find a unique solution. Unsolved trailing parameters may use declared default types; other cases require explicit arguments. Upper bounds and defaults of a type parameter may refer only to earlier declared parameters. After substitution, defaults and call-site arguments are checked by the same assignability relation.

`?` hides an existing argument; it is neither a raw type nor an escape from type checking. It primarily supports heterogeneous reflection collections such as `List<Field<User, ?>>` and `List<Function<?>>`.

## Definite assignment

Local variables and non-null fields must be initialized before reading. Every normally completing constructor path must initialize all required fields. Norm has no `late` initialization keyword that bypasses this rule.
