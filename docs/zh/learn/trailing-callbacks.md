# 57 尾随回调

最后一个函数实参可以写成尾随代码块。第一次调用通过 `in` 指定 Lambda 参数名，第二次使用推断出的参数名。

<<< ../../../norm/tests/docs/tour/trailing_callbacks.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/trailing_callbacks.out{text}

动手试试：把第二次调用的输入值从 4 改为 5，预测结果。

精确规则：[语言参考](/zh/spec/grammar/functions-advanced)。
