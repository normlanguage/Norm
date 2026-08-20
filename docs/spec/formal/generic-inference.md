# Norm Generic Inference Algorithm

## Overview

Norm uses explicit nominal generics with compiler assisted inference. The goal is to reduce verbosity without introducing hidden type-level programming.

Example:

```norm
List<User> users = repository.findAll()
```

The compiler infers the generic return type from the declared function signature.

## Inference Rules

The compiler resolves generic parameters in this order:

1. Explicit type arguments
2. Function parameter constraints
3. Return type context
4. Interface constraints
5. Bounds declared by extends

Example:

```norm
T first<T>(List<T> values)
```

Calling:

```norm
String name = first(values = names)
```

allows the compiler to infer T as String.

## Restrictions

Norm does not perform arbitrary type computation. Generic inference must remain predictable.
