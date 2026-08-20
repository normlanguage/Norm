# Norm ABI Register Allocation and Lowering

## Calling Convention

Norm native backend defines a stable ABI boundary between generated code and runtime.

Primitive values prefer register passing following platform ABI.

Object values are passed through managed references.

## Parameter Passing

| Norm Type | Native Representation |
|---|---|
| bool | i1 / byte |
| int | i32 |
| long | i64 |
| float | f32 |
| double | f64 |
| Ref<T> | managed pointer |
| String | runtime string pointer |

## Return Values

Small primitive values are returned directly. Large value objects use hidden return storage.

# GC Write Barrier

When an old generation object references a young object, runtime records the relationship.

Pseudo:

```
store_field(object, field, value):
    field = value
    if object.old && value.young:
        remember(object)
```

# Async Executor Design

Task states:

```
Created -> Ready -> Running -> Waiting -> Completed
```

Future provides:

```
poll()
await()
cancel()
```

# ORM Relation Design

Entity metadata:

```
@Entity
class User {
    Long id
}
```

Relations are explicit:

```
User.orders()
Order.user()
```

No hidden lazy loading.

# Router Matching

Router uses trie nodes:

```
root
 |- users
 |    |- :id
```

Matching priority:

1. static segment
2. parameter segment
3. wildcard

# Standard Library Expansion

API reference groups:

- String
- Collection
- IO
- Time
- HTTP
- SQL
- JSON
- Crypto

Each API defines types, methods, errors and examples.
