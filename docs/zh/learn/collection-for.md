# 22 集合字面量中的 `for`

集合字面量可以从另一个可迭代值生成元素。

<<< ../../../norm/tests/docs/tour/collection_for.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/collection_for.out{text}

带括号的 `for` 头位于 `[]` 内，对源集合的每个元素贡献一次 `number * 2`。它构造新列表，和迭代一节的语句循环不同。

动手试试：学完下一节后，为生成过程加筛选并比较输出。

详细规则：[参考](/zh/spec/grammar/literals)。
