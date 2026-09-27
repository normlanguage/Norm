# Generic Syntax

Type parameters follow a type or function name. Each parameter names a type within that declaration.

```norm
class Box<T> {
    T value
}

T first<T>(List<T> values) {
    return values[0]
}
```

## Bounds

```norm
T maximum<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(other: right) >= 0 { return left }
    return right
}
```

Each type parameter currently accepts only one nominal `extends` upper bound; there is no syntax for intersecting several bounds. The bound may be a class, an interface, or a previously declared type parameter. `U extends T` is validated after substituting the outer type arguments.

## Default types

Type parameters may declare default types. Defaulted parameters must form a contiguous suffix, may refer only to earlier declared type parameters, and must satisfy their own upper bounds.

```norm
enum Result<T, E = String> {
    Ok(T value, String msg = ""),
    Err(E error, String msg = "")
}
```

`Result<Integer>` equals `Result<Integer, String>`. Explicit `Result<Integer, Failure>` overrides the default. Defaults expand during semantic analysis; Core IR, the NAR public ABI, and runtime types still record both complete actual type arguments. This is not a raw type.

Class bounds are satisfied through class inheritance; interface bounds through explicitly declared `implements` or interface `extends` relationships. Matching members do not establish a relationship. Calls and bound method values for class methods within a constraint preserve virtual dispatch; interface method calls use dynamic interface dispatch.

## Use

A type position must supply all required arguments: raw types are invalid, and only trailing parameters declaring defaults may be omitted. A function or instance-method call may omit explicit type arguments when constraints give a unique solution; otherwise use `function<Type>(...)` or `receiver.method<Type>(...)` respectively.

Actual type arguments enter Core IR and the runtime type environment. Parameterized types are invariant. A diamond constructor omits only arguments uniquely solvable from constraints in that expression.
