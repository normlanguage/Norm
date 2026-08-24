# `Map<K, V>`

Map 把唯一键映射到值，键和值类型都必须完整声明。

```norm
Map<String, Integer> counts = Map<>()
counts.put(key: "open", value: 3)

if counts.containsKey("open") {
    Integer count = counts["open"]
}
```

## 缺失值

`map[key]` 要求键存在。`get(key:)` 在键不存在时返回 nullable value；需要区分缺失键和已保存的 nullable 值时使用 `containsKey(key:)`：

```norm
Integer value = 0
if counts.containsKey("closed") {
    value = counts["closed"]
}

Integer? optionalValue = counts.get(key: "closed")
```

## 键规则

键必须提供一致的 equality 与 hash。value 键按结构比较；class 键按对象 identity 比较，并使用稳定的 identity hash。Map 插入键和值时遵循各自的数据类别语义。

通用 Map 不承诺遍历顺序。需要插入顺序或排序时使用 OrderedMap 或 SortedMap，并显式提供 comparator。Map 自身遵循 value 语义。
