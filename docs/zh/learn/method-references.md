# 55 绑定方法引用

`counter.add` 成为绑定到这个 `counter` 对象的函数值。调用 `add(2)` 会更新同一对象的字段。

<<< ../../../norm/tests/docs/tour/method_references.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/method_references.out{text}

动手试试：创建另一个计数器，分别绑定它的 `add` 方法。

精确规则：[语言参考](/zh/spec/grammar/functions-advanced)。
