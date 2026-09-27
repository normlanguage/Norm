# `Set<T>`

Set stores distinct values. Uniqueness is determined jointly by the language's built-in equality and hash rules; `Equatable` and `Hashable` do not replace those rules.

```norm
Set<String> permissions = Set<>()
permissions.add("orders.read")

Boolean allowed = permissions.contains("orders.read")
```

`add` reports whether a new element was inserted; `remove` reports whether an element was found and removed. Value elements are deduplicated structurally, while class elements use object identity.

## Set operations

```norm
Set<String> all = left.union(right)
Set<String> common = left.intersection(right)
Set<String> onlyLeft = left.difference(right)
```

These operations return new sets without modifying their inputs. Any in-place mutable variants must have distinct names.

## Order and copying

A general Set does not guarantee iteration order. Set itself is a value; a copy has independent set structure, while class elements within it retain object identity.
