# Norm Collections Semantics

Collections are value containers by default.

```norm
List<User> a = users
List<User> b = a
```

The language meaning is independent copies. Runtime may optimize using copy-on-write.

Collections:
- List
- Array
- Map
- Set

No raw collections exist.
