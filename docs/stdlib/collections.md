# 集合

Norm 集合是类型化 value 容器。全部泛型实参都必须写出，raw type 非法。

```norm
List<Integer> first = List<Integer>(values: [1, 2, 3])
List<Integer> second = first
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

0.2 的 `map[key]` 要求键存在。返回 `Option<V>` 的安全查找与泛型数据 enum 一起交付。List 与 Array 索引同样要求下标有效。

## 迭代

Array、List、Set、Stack、Queue、Deque 与 Range 通过 `Iterable<T>` 暴露静态元素类型，因此 `for element : values` 可以推断循环变量。Map 迭代产生 `Pair<K, V>`。Stack 从栈顶到栈底迭代；通用 Map 和 Set 不承诺遍历顺序。

Array、List、Map、Set、Stack、Queue、Deque 与 Range 统一使用 `size()` 返回元素数量，不提供 `length` 属性。

## 0.2 序列函数

`std.collections` 已提供 `listContains`、`listCount`、`reversed` 与 `toList`。它们由 `std/collections/sequences.norm` 导出，使用时导入具体函数：

```norm
import std.collections.reversed

List<Integer> result = reversed(values: values)
```

详细签名见 [Collections API](/stdlib/collections-api)。
