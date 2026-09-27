# 56 扩展函数

扩展函数的首个参数在 `text.bracketed()` 中作为接收者。这改变调用形式，不会给 `String` 增加存储状态。

<<< ../../../norm/tests/docs/tour/extensions.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/extensions.out{text}

动手试试：用空字符串调用 `bracketed`。

精确规则：[语言参考](/zh/spec/grammar/functions)。
