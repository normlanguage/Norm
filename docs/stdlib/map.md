# `Map<K, V>`

Map 把唯一键映射到值，键和值类型都必须完整声明。

```norm
Map<String, int> counts = Map<String, int>()
counts.put(key = "open", value = 3)

Option<int> count = counts.get(key = "open")
```

## 缺失值

`get` 返回 `Option<V>`，因为 V 本身可能是 nullable，不能用 null 同时表示“键不存在”。需要缺失时计算值，可使用显式函数：

```norm
int value = counts.getOrElse(
    key = "closed",
    defaultValue = int() { return 0 }
)
```

## 键规则

键类型必须提供一致的 equality 和 hash：相等键必须有相同 hash。Map 在插入时保存键的独立值，因此调用者之后修改原 class 值不会破坏索引。

通用 Map 不承诺遍历顺序。需要插入顺序或排序时使用 OrderedMap 或 SortedMap，并显式提供 comparator。

Map 自身遵循值语义；共享可变 Map 使用 `Ref<Map<K, V>>`。迭代期间结构修改会使迭代器失效。

