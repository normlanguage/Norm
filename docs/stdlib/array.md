# `Array<T>`

Array is a fixed-length, index-updatable value container of one element type. Its length does not change after construction; use List when elements must be added or removed.

```norm
Array<Integer> scores = [80, 92, 75]
scores[1] = 95

Integer count = scores.size()
Integer first = scores[0]
```

## Construction

```norm
Array<String> names = ["first", "second", "third"]
Array<String> repeated = Array.filled(size: 3, value: "item")
```

An empty literal needs an expected element type. `Array.filled` fills a fixed number of positions with the same value; a negative size produces a stable argument error.

## Semantics

Assignment, argument passing, and return copy the Array container structure and apply ordinary assignment according to the element category. Value elements are logically independent; class elements retain object identity. Arrays use structural equality.

Valid indices satisfy `0 <= index < size()`. Out-of-bounds access produces `INDEX_OUT_OF_BOUNDS` (`NORM-RUNTIME-0001`). Array implements `Iterable<T>`, so loop variables can be inferred as `T`.
