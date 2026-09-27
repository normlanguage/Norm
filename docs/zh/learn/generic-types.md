# 47 泛型类型

`Box<T>` 定义一种结构，`item` 的类型由 `T` 决定。`Box<Integer>` 与 `Box<String>` 的字段类型分别受检查，值相等仍按内容比较。

<<< ../../../norm/tests/docs/tour/generic_types.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/generic_types.out{text}

动手试试：尝试把 `label.item` 赋给 `Integer` 变量。

精确规则：[语言参考](/zh/spec/grammar/generics)。
