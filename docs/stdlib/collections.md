# 集合

Norm 集合是类型化 value 容器。全部泛型实参都必须写出，raw type 非法。

```norm
List<Integer> first = [1, 2, 3]
List<Integer> second = first
second.add(4)
```

执行后 `first` 的结构不变。若元素是 class，两个容器仍然保存同一对象身份。

## 类型

| 类型 | 用途 |
| --- | --- |
| `Array<T>` | 固定长度、连续索引序列 |
| `List<T>` | 可增长的有序序列 |
| `MutableList<T>` | 具有共享 identity 的可变有序序列视图 |
| `MutableSet<T>` | 具有共享 identity 的可变唯一元素集合视图 |
| `MutableCollection<T>` | Java 引用集合的共同可变基类 |
| `MutableMap<K, V>` | 具有共享 identity 的可变映射视图 |
| `IterableView<T>` | 具有共享 identity 的只读迭代视图 |
| `IteratorView<T>` | 具有共享 cursor 状态的迭代器视图 |
| `Map<K, V>` | 唯一键到值的映射 |
| `Set<T>` | 按 equality 与 hash 去重 |
| `Stack<T>` | LIFO 序列 |
| `Queue<T>` | FIFO 序列 |
| `Deque<T>` | 双端序列 |
| `Pair<A, B>` | 两个类型化值的组合 |
| `Range` | 右端不包含的整数区间 |

## 缺失与越界

`map[key]` 要求键存在，`map.get(key:)` 在缺失时返回 null。List 与 Array 索引要求下标有效。需要区分缺失键和已保存的 nullable 值时使用 `containsKey(key:)`。

## 迭代

Array、List、Set、Stack、Queue、Deque 与 Range 显式实现 `Iterable<T>`，因此 `for element : values` 可以从该 interface 的类型实参推断循环变量。Map 实现 `Iterable<Pair<K, V>>`。Stack 从栈顶到栈底迭代；通用 Map 和 Set 不承诺遍历顺序。

Array、List、Map、Set、Stack、Queue、Deque 与 Range 统一使用 `size()` 返回元素数量，不提供 `length` 属性。

引用集合是 class，复制变量会共享同一对象；其成员对同一宿主集合原位生效。`MutableList<T>` 与 `MutableSet<T>` 继承 `MutableCollection<T>`，并和 `IterableView<T>` 一样实现 `Iterable<T>`。Java Binding 使用这些类型，因此不会把 Java 引用语义混入值语义集合。

## 序列成员

```norm
Integer last = values.last()
List<Integer> result = values.reversed()
List<Integer> zeros = List.filled(size: 8, value: 0)
```

`Array<T>` 和 `List<T>` 的 `reversed()` 返回独立副本；`List<T>.removeLast()` 删除并返回尾元素。`Array.filled` 与 `List.filled` 是通过类型名称调用的泛型类型级成员。

`std.collections` 提供自然顺序 `sort`，使用时导入具体函数：

```norm
import std.collections.sort

List<Integer> ordered = sort(values: values)
```

完整签名以 [`std.collections.sequences`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/collections/sequences.norm) 与 [`std.collections.mutable`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/collections/mutable.norm) 为准。
