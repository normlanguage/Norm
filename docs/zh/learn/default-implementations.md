# 44 接口默认方法

`Named` 中的 `label()` 自带方法体，实现类只需提供 `name()` 就能使用它。默认方法通过 `this` 调用实际实现。

<<< ../../../norm/tests/docs/tour/default_implementations.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/default_implementations.out{text}

动手试试：在 `Task` 中增加 `label()`，观察调用哪一个方法体。

精确规则：[语言参考](/zh/spec/grammar/interfaces)。
