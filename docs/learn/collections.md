# 07 集合与迭代

集合字面量由期望类型决定具体容器；遍历只依赖 `Iterable<T>`，不是某个内建集合的特殊语法。

<<< ../../norm/tests/docs/tour/07_collections.norm{norm}

输出：

```text
0
4
1
5
2
6
1
```

## Array 与 List

`Array<T>` 表达固定长度的索引序列，`List<T>` 表达可调整长度的序列。同一个 `[]` 可以根据赋值目标物化为不同容器；没有上下文的空字面量会被拒绝。

内建集合采用 value 语义：复制容器会得到逻辑独立的结构。如果元素是 class，元素指向的对象身份仍然共享。

## For

遍历式 `for` 可以绑定元素，也可以同时绑定从零开始的索引：

```norm
for value : values {
  printLine(value)
}

for value, index : values {
  printLine(index)
}
```

条件循环写作 `for condition {}`。需要从循环产生值时使用 for 表达式，并由 `break value` 与 `else` 明确正常耗尽路径。

容器 API 见[标准库集合](/stdlib/collections)，控制规则见[循环参考](/spec/grammar/loops)。

上一章：[Null 与类型推断](/learn/nullability-inference)。下一章：[Lambda 与 Extension](/learn/lambdas-extensions)。
