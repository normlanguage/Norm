# 43 实现接口

`implements Named` 承诺提供匹配的 `name()` 方法。`value` 与 `class` 都能实现接口，调用方只依赖 `Named`。

<<< ../../../norm/tests/docs/tour/interface_implementation.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/interface_implementation.out{text}

动手试试：删去 `name()`，观察编译器如何指出未完成的接口实现。

精确规则：[语言参考](/zh/spec/grammar/interfaces)。
