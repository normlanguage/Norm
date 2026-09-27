# 49 泛型推断

`Entry<>` 从具名构造实参推断两个类型参数；`choose` 也从实参推断 `T`，调用点不必写出类型参数。

<<< ../../../norm/tests/docs/tour/generic_inference.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/generic_inference.out{text}

动手试试：把 `Entry` 的第二个值和 `right` 实参都改为整数，观察 `selected` 的推断类型。

精确规则：[语言参考](/zh/spec/formal/generic-inference)。
