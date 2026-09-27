# 36 空值回退

`??` 只在左侧为 null 时使用右侧表达式。

<<< ../../../norm/tests/docs/tour/fallback.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/fallback.out{text}

第一次输出 `Norm`，没有调用 `fallback()`。第二次先输出 `computed` 再输出 `guest`，证明只在缺失时计算回退函数。

动手试试：也把 `present` 改为 null，数一数回退函数执行几次。

详细规则：[参考](/zh/spec/grammar/expressions)。
