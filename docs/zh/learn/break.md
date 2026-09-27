# 16 结束循环

不带值的 `break` 会立即结束当前语句循环。

<<< ../../../norm/tests/docs/tour/break.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/break.out{text}

`stop` 不会被输出，它后面的元素也不会被访问；控制流直接来到循环之后。最后一行说明外层程序仍继续执行。

动手试试：把 `stop` 移到末尾，比较输出。

详细规则：[参考](/zh/spec/grammar/loops)。
