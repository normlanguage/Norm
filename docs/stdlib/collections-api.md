# Collections API

本页定义集合 API 的首版草案。集合保持完整泛型信息，并遵循 Norm 的默认值语义；赋值一个集合不会让两处后续修改互相影响。

## `List<T>`

```norm
List<String> names = ["Ada", "Lin"]
Integer size = names.size()
String first = names[0]

names.add(value: "Grace")
names.removeAt(index: 1)
String last = names.last()
String removed = names.removeLast()
List<String> backwards = names.reversed()
```

核心操作包括 `size()`、`isEmpty()`、`get(index)`、`set(index, value)`、`add(value)`、`insert(index, value)`、`removeAt(index)`、`last()`、`removeLast()`、`reversed()` 和 `clear()`。索引越界以及从空 List 读取或删除尾元素产生对应的稳定运行错误。

```norm
List<Integer> zeros = List.filled(size: 8, value: 0)
```

`filled` 创建指定长度的 List，每个位置按照普通赋值语义保存 value。负数 size 产生 `INVALID_ARGUMENT`。

## `Array<T>`

```norm
Array<Boolean> visited = Array.filled(size: 8, value: false)
Boolean last = visited.last()
Array<Boolean> backwards = visited.reversed()
```

Array 长度固定，因此提供 `last()`、`reversed()` 和类型级 `filled()`，不提供删除尾元素的操作。

## `Map<K, V>`

```norm
Map<String, Integer> counts = Map<>()
counts.put(key: "ready", value: 3)

if counts.containsKey(key: "ready") {
    Integer count = counts["ready"]
}
Boolean present = counts.containsKey(key: "ready")
Integer? missing = counts.get(key: "missing")
```

索引操作要求键存在，`get` 在缺失时返回 nullable value。键必须提供稳定的相等与哈希语义。遍历顺序不是通用 Map 契约的一部分；需要稳定顺序时使用专门类型。

## `Set<T>`

```norm
Set<String> tags = Set<>()
Boolean inserted = tags.add(value: "stable")
Boolean contains = tags.contains(value: "stable")
```

Set 根据元素的数据类别判断唯一性：value 使用结构 equality 与 hash，class 使用稳定的 identity equality 与 hash。插入时执行普通赋值语义。

## 排序

`std.collections.sort` 为 Integer、CodePoint 和 String 的 List 与 Array 提供自然顺序重载：

```norm
import std.collections.sort

List<Integer> ordered = sort(values: values)
Array<CodePoint> signature = sort(values: word.codePoints())
```

排序稳定并返回独立集合。Integer 使用数值顺序，CodePoint 使用 Unicode scalar value，String 使用 code point 字典序。输入集合保持不变。

## `Range`

```norm
Range ascending = range(start: 0, end: 10, step: 2)
Range descending = range(start: 5, end: -1, step: -1)
```

Range 始终左闭右开。正 step 在 current 小于 end 时继续，负 step 在 current 大于 end 时继续；方向与边界不匹配时得到空 Range。step 为零产生 `INVALID_ARGUMENT`。`size()` 与迭代使用相同规则，边界计算和迭代加法检测 Integer 溢出。

## 迭代与共享

集合显式实现 `Iterable<T>`，可直接用于 `for item : values` 并推断 `T`。结构修改会使已有 Iterator 失效。确实需要多处共享同一个 value 存储位置时，使用 `ref<List<T>>`。
