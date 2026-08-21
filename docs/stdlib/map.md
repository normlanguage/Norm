# `Map<K, V>`

Map 把唯一键映射到值，键和值类型都必须完整声明。

```norm
Map<String, int> counts = Map<String, int>()
counts.put(key: "open", value: 3)

Option<int> count = counts.get("open")
```

## 缺失值

`get` 返回 `Option<V>`，因为 V 本身可能为 nullable，不能再用 null 表示“键不存在”。需要缺失时计算默认值，可以使用：

```norm
int value = counts.getOrElse(
    key: "closed",
    defaultValue: int() { return 0 }
)
```

## 键规则

键必须提供一致的 equality 与 hash。value 键按结构比较；class 键按对象 identity 比较，并使用稳定的 identity hash。Map 插入键和值时遵循各自的数据类别语义。

通用 Map 不承诺遍历顺序。需要插入顺序或排序时使用 OrderedMap 或 SortedMap，并显式提供 comparator。Map 自身遵循 value 语义。

