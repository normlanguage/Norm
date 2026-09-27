# 37 普通枚举

枚举列出有限个具名选项。

<<< ../../../norm/tests/docs/tour/plain_enums.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/plain_enums.out{text}

`State.Ready` 与 `State.Done` 是同一类型的不同变体。变量可以改变所持有的变体，而枚举声明限定了全部可能状态。

动手试试：加入 `Paused` 变体，并在 `main` 中赋值。

详细规则：[参考](/zh/spec/enum-design)。
