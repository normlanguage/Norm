# `Array<T>`

Array 是固定长度、可按索引更新的同类型值容器。长度在构造后不变；需要增删元素时使用 List。

```norm
Array<int> scores = Array<int>(values = [80, 92, 75])
scores[1] = 95

int count = scores.length
int first = scores[0]
```

## 构造

```norm
Array<String> names = Array<String>(
    length = 3,
    initialize = String(int index) {
        return "item-${index}"
    }
)
```

非空元素类型不能创建未初始化槽位。初始化函数对每个索引执行一次，失败时整个构造失败且不会暴露半初始化 Array。

## 语义

Array 赋值与传参遵循值语义。切片返回独立 Array 或明确的只读 view 类型，不能用同一个 API 隐藏共享。索引范围是 `0 <= index < length`，越界抛出 `IndexError`。

Array 实现迭代协议。结构长度不变，但遍历期间修改元素是否允许由具体迭代器类型明确规定。

