# 54 捕获外部值

`multiplier` 返回后，Lambda 仍可读取外层的 `factor`。每次调用传入新的 `number`，同时沿用被捕获的倍数。

<<< ../../../norm/tests/docs/tour/capture.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/capture.out{text}

动手试试：再创建一个倍数为 5 的函数值。

精确规则：[语言参考](/zh/spec/grammar/functions-advanced)。
