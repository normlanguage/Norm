# Output API

`printLine(value)` is the single-line standard output primitive. For batch output, import `std.io.printLines`. Its public declaration is defined by [`io/output.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/io/output.norm).

```norm
Void printLines<T extends Stringable>(Iterable<T> values)
```

Elements are printed one per line in `Iterable<T>` traversal order. `Stringable` is the standard interface declaring `String toString()`; the compiler provides witnesses for basic scalar types, while custom types must explicitly implement the interface. A literal containing different concrete types uses `Stringable` as its element type when they share that interface.
