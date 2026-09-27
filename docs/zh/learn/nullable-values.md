# 33 可空值

类型后的 `?` 允许该类型的值为 `null`；没有 `?` 的类型不允许。

<<< ../../../norm/tests/docs/tour/nullable_values.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/nullable_values.out{text}

`label` 可以返回字符串或缺失值，因此结果类型是 `String?`。两个绑定都保持可空类型，即使其中一次调用当前返回字符串；需要非空字符串时先检查或提供回退值。

动手试试：让 false 分支也返回字符串，比较两次空值检查。

详细规则：[参考](/zh/spec/type-system)。
