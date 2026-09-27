# 35 安全访问

`?.` 只在接收者存在时读取成员。

<<< ../../../norm/tests/docs/tour/safe_access.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/safe_access.out{text}

对 `missing`，`missing?.title` 直接得到 null，不读取字段；对 `present`，它得到任务标题。整个表达式仍是可空的。

动手试试：再加入一个可空成员，连续使用两次安全访问。

详细规则：[参考](/zh/spec/grammar/expressions)。
