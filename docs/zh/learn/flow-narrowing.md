# 34 控制流收窄

空值检查可以在分支内证明可空值实际非空。

<<< ../../../norm/tests/docs/tour/flow_narrowing.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/flow_narrowing.out{text}

在 `else` 分支中，`value` 不可能是 null，因此可以调用 String 的 `codePointSize()`。若值可能变化，离开该分支后这个证明不一定继续成立。

动手试试：反转条件，并把长度调用移到非空分支。

详细规则：[参考](/zh/spec/type-system)。
