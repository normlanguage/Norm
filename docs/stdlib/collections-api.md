# Collections API

本页定义集合 API 的首版草案。集合保持完整泛型信息，并遵循 Norm 的默认值语义；赋值一个集合不会让两处后续修改互相影响。

## `List<T>`

```norm
List<String> names = List<String>(values: ["Ada", "Lin"])
Integer size = names.size()
String first = names[0]

names.add(value: "Grace")
names.removeAt(index: 1)
```

核心操作包括 `size()`、`isEmpty()`、`get(index)`、`set(index, value)`、`add(value)`、`insert(index, value)`、`removeAt(index)` 和 `clear()`。越界索引属于程序错误并抛出 `IndexError`。

## `Map<K, V>`

```norm
Map<String, Integer> counts = Map<String, Integer>()
counts.put(key: "ready", value: 3)

if counts.containsKey(key: "ready") {
    Integer count = counts["ready"]
}
Boolean present = counts.containsKey(key: "ready")
```

0.2 的索引操作要求键存在，不使用 null 表示缺失。返回 `Option<V>` 的安全 `get` 与携带数据的泛型 enum 一起交付。键必须提供稳定的相等与哈希语义。遍历顺序不是通用 Map 契约的一部分；需要稳定顺序时使用专门类型。

## `Set<T>`

```norm
Set<String> tags = Set<String>()
Boolean inserted = tags.add(value: "stable")
Boolean contains = tags.contains(value: "stable")
```

Set 根据元素的数据类别判断唯一性：value 使用结构 equality 与 hash，class 使用稳定的 identity equality 与 hash。插入时执行普通赋值语义。

## 迭代与共享

集合实现统一迭代协议，可直接用于 `for item : values` 并推断 `T`。结构修改会使已有迭代器失效。确实需要多处共享同一个 value 存储位置时，使用 `ref<List<T>>`。
