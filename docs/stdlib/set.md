# `Set<T>`

Set 保存不重复的值，唯一性由 T 的 equality 与 hash 共同决定。

```norm
Set<String> permissions = Set<String>()
permissions.add("orders.read")

bool allowed = permissions.contains("orders.read")
```

`add` 返回是否实际插入新元素，`remove` 返回是否找到并移除元素。value 元素按结构去重；class 元素按对象 identity 去重。

## 集合运算

```norm
Set<String> all = left.union(right)
Set<String> common = left.intersection(right)
Set<String> onlyLeft = left.difference(right)
```

这些操作返回新 Set，不修改输入。可变原地版本如果提供，名称必须明确区分。

## 顺序与复制

通用 Set 不保证遍历顺序。Set 自身是 value；复制后集合结构独立，其中的 class 元素仍保留对象身份。

