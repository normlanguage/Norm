# Collections

Norm collections are typed value containers. Every generic type argument must be written; raw types are invalid.

```norm
List<Integer> first = [1, 2, 3]
List<Integer> second = first
second.add(4)
```

After this code runs, the structure of `first` is unchanged. If the elements are classes, both containers still hold the same object identities.

## Types

| Type | Purpose |
| --- | --- |
| `Array<T>` | Fixed-length, continuously indexed sequence |
| `List<T>` | Growable ordered sequence |
| `MutableList<T>` | Mutable ordered-sequence view with shared identity |
| `MutableSet<T>` | Mutable unique-element set view with shared identity |
| `MutableCollection<T>` | Common mutable base for Java reference collections |
| `MutableMap<K, V>` | Mutable map view with shared identity |
| `IterableView<T>` | Read-only iteration view with shared identity |
| `IteratorView<T>` | Iterator view with shared cursor state |
| `Map<K, V>` | Mapping from unique keys to values |
| `Set<T>` | Deduplication by equality and hash |
| `Stack<T>` | LIFO sequence |
| `Queue<T>` | FIFO sequence |
| `Deque<T>` | Double-ended sequence |
| `Pair<A, B>` | Pair of typed values |
| `Range` | Integer range with an exclusive upper bound |

## Missing values and bounds

`map[key]` requires the key to exist; `map.get(key:)` returns null when it is missing. List and Array indices must be valid. Use `containsKey(key:)` to distinguish a missing key from a stored nullable value.

## Iteration

Array, List, Set, Stack, Queue, Deque, and Range explicitly implement `Iterable<T>`, so `for element : values` can infer its loop variable from the interface type argument. Map implements `Iterable<Pair<K, V>>`. Stack iterates from top to bottom; general Map and Set do not promise traversal order.

Array, List, Map, Set, Stack, Queue, Deque, and Range consistently use `size()` for their element count and do not provide a `length` property.

Reference collections are classes: copying a variable shares the same object, and member operations affect the same host collection in place. `MutableList<T>` and `MutableSet<T>` inherit `MutableCollection<T>` and, like `IterableView<T>`, implement `Iterable<T>`. Java Binding uses these types so Java reference semantics do not leak into value-semantic collections.

## Sequence members

```norm
Integer last = values.last()
List<Integer> result = values.reversed()
List<Integer> zeros = List.filled(size: 8, value: 0)
```

`reversed()` on `Array<T>` and `List<T>` returns an independent copy. `List<T>.removeLast()` removes and returns the last element. `Array.filled` and `List.filled` are generic type-level members called through their type names.

`std.collections` provides natural-order `sort`; import the specific function to use it:

```norm
import std.collections.sort

List<Integer> ordered = sort(values: values)
```

[`std.collections.sequences`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/collections/sequences.norm) and [`std.collections.mutable`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/collections/mutable.norm) define the complete signatures.
