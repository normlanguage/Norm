# 15 条件循环

条件式 `for` 会在布尔条件为真时重复执行。

<<< ../../../norm/tests/docs/tour/conditional_loops.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/conditional_loops.out{text}

每轮开始前都会检查条件，因此初始条件为假时循环体一次也不执行。循环体更新 `remaining`，让此例最终结束。

动手试试：把初始值改成零，预测输出。

详细规则：[参考](/zh/spec/grammar/loops)。
