# 58 具名回调参数

第一个尾随代码块直接使用回调参数名 `title`；第二个通过 `renamed in` 给同一传入值另取局部名称。

<<< ../../../norm/tests/docs/tour/named_callbacks.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/named_callbacks.out{text}

动手试试：修改提交的标题，核对两个回调都收到新值。

精确规则：[语言参考](/zh/spec/grammar/functions-advanced)。
