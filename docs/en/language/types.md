# Types and Null

Norm is statically typed and non-null by default. `T?` explicitly adds null to a type.

`Any` is the static top type for every non-null value, while `Any?` also accepts null. Values widen safely to `Any`; `Any` does not implicitly narrow to a concrete type and exposes no concrete-type members. Interfaces and bounded generics remain the way to require common behavior.

`Integer` and `Long` are signed 32-bit and 64-bit integers. `Float` and `Double` are binary floating-point leaves. Abstract `Number` stores any of those leaves while preserving its concrete runtime representation. Numeric literals use a concrete expected leaf when available; otherwise integers default to `Integer` or `Long` by range and decimal literals default to `Double`.

```norm
Number count = 10
List<Number> values = [1, 2.5, 3]
```

```norm
String name = "Norm"
String? nickname = null
```

Nullable values must be handled before direct member access:

```norm
if nickname != null {
  printLine(nickname.codePointSize())
}
```

Early return and Boolean short-circuiting also participate in flow-sensitive narrowing. Reassigning a local updates its flow state. Mutable fields are read into a local before narrowing so the checked value and the used value are the same read.

## Safe access and fallback

```norm
Integer? citySize = user.address?.city?.codePointSize()
String displayName = user.nickname ?? user.name
```

Safe access evaluates its receiver once and skips the member call and arguments when the receiver is null. Null coalescing evaluates its right side only when the left side is null.

## Generic composition

```norm
List<String>? optionalNames = null
List<String?> names = ["Norm", null]
```

Nullability applies to the complete adjacent type. Generic substitution normalizes repeated nullability, so substituting `String?` for `T` in `T?` still produces `String?`.

`null` requires a nullable expected type. It cannot independently infer an arbitrary type.

Next: [Value and identity](/en/language/objects).
