# 输出 API

`printLine(value)` 是标准输出的单行原语。批量输出导入 `std.io.printLines`，其公共声明以 [`io/output.norm`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/io/output.norm) 为准。

```norm
Void printLines<T extends Stringable>(Iterable<T> values)
```

元素按照 `Iterable<T>` 的遍历顺序逐行输出。`Stringable` 是声明 `String toString()` 的标准 interface；基础标量类型由编译器提供 witness，自定义类型需要显式实现该接口。不同具体类型组成的字面量在共享 `Stringable` 时以该接口作为元素类型。
