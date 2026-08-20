# Norm Type System Formal Specification

## 1. Type Model

Norm uses a nominal static type system. A type is identified by its declaration, not by structural compatibility.

The major categories are:

- Primitive types
- Value types
- Class types
- Interface types
- Enum types
- Function types
- Nullable types
- Generic types

A compiler must resolve every expression to a known type before code generation.

## 2. Null Safety

Nullable is an explicit type constructor.

```
String name = "Norm"
String? nickname = null
```

`String` and `String?` are different types.

The compiler maintains a null-state during semantic analysis. A variable can be narrowed after checks:

```
if user.email != null {
    print(user.email)
}
```

The compiler may prove that the value is non-null inside the branch.

## 3. Value and Reference Semantics

Norm separates value copying from shared identity.

Normal assignment:

```
User b = a
```

means a value copy at language level.

Explicit sharing:

```
Ref<User> b = a.ref()
```

creates shared identity.

The optimizer may use copy-on-write internally, but observable behavior must follow value semantics.

## 4. Generic Types

Generics are reified.

Example:

```
List<String>.class
```

contains the generic argument metadata.

The runtime must preserve enough information for reflection and serialization.
