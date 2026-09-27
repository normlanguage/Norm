# 45 类继承

`NamedCounter extends Counter` 继承基类字段，并增加 `name`。构造函数先通过 `super(initial: initial)` 初始化基类，再设置新字段。

<<< ../../../norm/tests/docs/tour/inheritance.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/inheritance.out{text}

动手试试：修改初始计数，核对继承字段的值。

精确规则：[语言参考](/zh/spec/grammar/classes)。
