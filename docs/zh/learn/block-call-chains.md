# 59 串联代码块调用

`produce { 7 }` 通过回调创建值，`map { item * 2 }` 再变换它。每个代码块的期望函数类型约束参数与返回值。

<<< ../../../norm/tests/docs/tour/block_call_chains.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/block_call_chains.out{text}

动手试试：把第一个代码块改为返回 9，追踪结果。

精确规则：[语言参考](/zh/spec/grammar/functions-advanced)。
