# Collections

Java collection conversion is defined in [sequences.norm](../../norm/stdlib/std/collections/sequences.norm); [JavaCollectionTaskInteropTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaCollectionTaskInteropTest.java) verifies its Java boundary contract.

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

Java reference collections are declared by [`java.base`](../../norm/stdlib/java/base/module.norm). [`mutableMap`](../../norm/stdlib/std/collections/mutable.norm) constructs a Java map with shared identity through ordinary bindings. See [Java bindings](/design/java-library-adapters) for their calling and iteration contracts.

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
