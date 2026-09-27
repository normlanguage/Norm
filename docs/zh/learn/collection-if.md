# 23 集合字面量中的 `if`

集合字面量可以只在条件成立时加入一个元素。

<<< ../../../norm/tests/docs/tour/collection_if.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/collection_if.out{text}

这里 `includeExtra` 为假，`inspect` 完全不产生元素，因此 `run` 位于索引一。它不同于普通 `if` 表达式在两个值之间选一个。

动手试试：把标记改为真，预测列表长度和索引一的元素。

详细规则：[参考](/zh/spec/grammar/literals)。
