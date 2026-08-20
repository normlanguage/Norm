# Norm Standard Collections Design

## Philosophy

Collections are value containers by default.

```
List<User> a = users
List<User> b = a
```

has value semantics.

The runtime may use copy-on-write internally.

## List

Example:

```norm
List<String> names = List<String>(
    values = ["Alice", "Bob"]
)
```

## Map

Maps are typed:

```norm
Map<String, User> users
```

Raw containers are forbidden.

## Set

Set provides unique values based on equality semantics.
