# 17 跳过本轮

`continue` 跳过当前一轮的剩余语句，然后进入下一轮。

<<< ../../../norm/tests/docs/tour/continue.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/continue.out{text}

只有 `skip` 被略过；后续元素仍会执行，这正是它和上一节 `break` 的区别。

动手试试：把跳过的值改为 `read`，预测输出。

详细规则：[参考](/zh/spec/grammar/loops)。
