# 07 Collections and iteration

The expected type determines the concrete container for a collection literal. Iteration depends only on `Iterable<T>`, not special syntax for a built-in collection.

<<< ../../norm/tests/docs/tour/07_collections.norm{norm}

Output:

```text
0
4
1
5
2
6
1
```

## Array and List

`Array<T>` is an indexed sequence of fixed length; `List<T>` can change length. The same `[]` can become different containers depending on the assignment target. An empty literal without context is rejected.

Built-in collections have value semantics: copying a container produces a logically independent structure. If an element is a class instance, that element's object identity remains shared.

## For

An iteration-style `for` can bind the element alone or also bind its zero-based index:

```norm
for value : values {
  printLine(value)
}

for value, index : values {
  printLine(index)
}
```

A conditional loop is written `for condition {}`. To produce a value from a loop, use a for expression with `break value` and an `else` for normal exhaustion.

See the [standard-library collections](/stdlib/collections) for container APIs and the [loop reference](/spec/grammar/loops) for control rules.

Previous: [Null and type inference](/learn/nullability-inference). Next: [Lambda and Extension](/learn/lambdas-extensions).
