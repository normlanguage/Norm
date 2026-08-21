# `Array<T>`

Array 是固定长度、可按索引更新的同类型 value 容器。长度在构造后不变；需要增删元素时使用 List。

```norm
Array<int> scores = Array<int>(values: [80, 92, 75])
scores[1] = 95

int count = scores.length
int first = scores[0]
```

## 构造

```norm
Array<String> names = Array<String>(
    length: 3,
    initialize: String(int index) {
        return "item-${index}"
    }
)
```

非空元素类型不能创建未初始化槽位。初始化函数对每个索引执行一次；失败时不会暴露半初始化 Array。

## 语义

Array 赋值、传参和返回会复制容器结构，并按元素类别执行普通赋值。value 元素逻辑独立，class 元素保留对象身份。Array 使用结构相等。

索引范围是 `0 <= index < length`，越界产生 `IndexError`。Array 实现 `Iterable<T>`，因此循环变量可以推断为 `T`。

