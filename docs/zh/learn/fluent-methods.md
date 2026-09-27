# 32 链式方法

方法可以返回 `this`，让调用方继续操作同一对象。

<<< ../../../norm/tests/docs/tour/fluent_methods.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/fluent_methods.out{text}

每次 `add` 先修改计数器，再返回该计数器本身。第二次调用因此接收第一次调用的结果；最终数值包含两次相加。

动手试试：把链式调用拆成两条语句，确认输出相同。

详细规则：[参考](/zh/spec/grammar/classes)。
