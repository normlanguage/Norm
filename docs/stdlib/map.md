# `Map<K, V>`

Map associates unique keys with values. Both key and value types must be fully declared.

```norm
Map<String, Integer> counts = Map<>()
counts.put(key: "open", value: 3)

if counts.containsKey("open") {
    Integer count = counts["open"]
}
```

## Missing values

`map[key]` requires the key to exist. `get(key:)` returns a nullable value when it does not. Use `containsKey(key:)` to distinguish a missing key from a stored nullable value:

```norm
Integer value = 0
if counts.containsKey("closed") {
    value = counts["closed"]
}

Integer? optionalValue = counts.get(key: "closed")
```

## Key rules

Map uses the language's consistent built-in equality and hash rules: value keys are computed recursively by structure, while class keys use object identity. `Equatable` and `Hashable` do not replace these rules. Inserting keys and values follows the semantics of their respective data categories.

A general Map does not promise iteration order. Use OrderedMap or SortedMap with an explicit comparator when insertion or sorted order is needed. Map itself has value semantics.
