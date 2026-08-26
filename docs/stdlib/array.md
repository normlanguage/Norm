# `Array<T>`

Array 是固定长度、可按索引更新的同类型 value 容器。长度在构造后不变；需要增删元素时使用 List。

```norm
Array<Integer> scores = [80, 92, 75]
scores[1] = 95

Integer count = scores.size()
Integer first = scores[0]
```

## 构造

```norm
Array<String> names = ["first", "second", "third"]
Array<String> repeated = Array.filled(size: 3, value: "item")
```

空字面量需要期望元素类型。`Array.filled` 使用同一个值填充固定数量的位置，size 为负数时产生稳定的参数错误。

## 语义

Array 赋值、传参和返回会复制容器结构，并按元素类别执行普通赋值。value 元素逻辑独立，class 元素保留对象身份。Array 使用结构相等。

索引范围是 `0 <= index < size()`，越界产生 `INDEX_OUT_OF_BOUNDS`（`NORM-RUNTIME-0001`）。Array 实现 `Iterable<T>`，因此循环变量可以推断为 `T`。
