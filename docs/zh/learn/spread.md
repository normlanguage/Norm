# 24 集合展开

`...` 会把可迭代值中的元素插入集合字面量中的一个位置。

<<< ../../../norm/tests/docs/tour/spread.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/spread.out{text}

`middle` 中的 `run` 和 `check` 按顺序插入，`tail` 在末尾提供 `share`。两个源列表都不会作为单个嵌套元素插入。

动手试试：展开一个空列表，观察新长度。

详细规则：[参考](/zh/spec/grammar/literals)。
