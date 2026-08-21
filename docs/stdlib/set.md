# `Set<T>`

Set 保存不重复的值，唯一性由 T 的 equality 与 hash 共同决定。

```norm
Set<String> permissions = Set<String>()
permissions.add(value = "orders.read")

bool allowed = permissions.contains(value = "orders.read")
```

`add` 返回是否实际插入新元素，`remove` 返回是否找到并移除元素。重复 add 不改变集合。

## 集合运算

```norm
Set<String> all = left.union(other = right)
Set<String> common = left.intersection(other = right)
Set<String> onlyLeft = left.difference(other = right)
```

这些操作返回新 Set，不修改输入。可变原地版本如果提供，名称必须明确区分。

## 顺序与复制

通用 Set 不保证迭代顺序。需要稳定输出时先排序或使用 OrderedSet。插入 class 值时保存独立副本，之后修改原变量不会改变集合成员身份。

Set 赋值遵循值语义，共享修改需显式使用 Ref。

