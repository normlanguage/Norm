# Collections API

本页定义集合 API 的首版草案。集合保持完整泛型信息，并遵循 Norm 的默认值语义；赋值一个集合不会让两处后续修改互相影响。

## `List<T>`

```norm
List<String> names = List<String>(values = ["Ada", "Lin"])
int size = names.size
String first = names[0]

names.add(value = "Grace")
names.removeAt(index = 1)
```

核心操作包括 `size`、`isEmpty()`、`get(index)`、`set(index, value)`、`add(value)`、`insert(index, value)`、`removeAt(index)` 和 `clear()`。越界索引属于程序错误并抛出 `IndexError`。

## `Map<K, V>`

```norm
Map<String, int> counts = Map<String, int>()
counts.put(key = "ready", value = 3)

Option<int> count = counts.get(key = "ready")
bool present = counts.containsKey(key = "ready")
```

`get` 返回 `Option<V>`，不会用 null 表示缺失。键必须提供稳定的相等与哈希语义。遍历顺序不是通用 Map 契约的一部分；需要稳定顺序时使用专门类型。

## `Set<T>`

```norm
Set<String> tags = Set<String>()
bool inserted = tags.add(value = "stable")
bool contains = tags.contains(value = "stable")
```

Set 根据值相等判断唯一性。修改已作为键或元素参与哈希的可变 class 值是非法用法；API 应在插入时保存独立值。

## 迭代与共享

集合实现统一迭代协议，可直接用于 `for T item : values`。结构修改会使已有迭代器失效。确实需要多处共享修改同一个集合时，使用 `Ref<List<T>>` 一类显式共享类型。

