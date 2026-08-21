# 集合

Norm 集合是类型化 value 容器。全部泛型实参都必须写出，raw type 非法。

```norm
List<int> first = List<int>(values: [1, 2, 3])
List<int> second = first
second.add(4)
```

执行后 `first` 的结构不变。若元素是 class，两个容器仍然保存同一对象身份。

## 类型

| 类型 | 用途 |
| --- | --- |
| `Array<T>` | 固定长度、连续索引序列 |
| `List<T>` | 可增长的有序序列 |
| `Map<K, V>` | 唯一键到值的映射 |
| `Set<T>` | 按 equality 与 hash 去重 |
| `Stack<T>` | LIFO 序列 |
| `Queue<T>` | FIFO 序列 |
| `Deque<T>` | 双端序列 |
| `Pair<A, B>` | 两个类型化值的组合 |
| `Range` | 右端不包含的整数区间 |

## 缺失与越界

Map 查找和可能找不到的集合操作返回 Option。List 与 Array 索引越界产生 `IndexError`，不使用 null 同时表达“没有元素”和“元素为空”。

## 迭代

集合通过 `Iterable<T>` 暴露静态元素类型，因此 `for element : values` 可以推断循环变量。通用 Map 和 Set 不承诺遍历顺序；需要稳定顺序时使用专门集合或显式排序。

详细签名见 [Collections API](/stdlib/collections-api)。

