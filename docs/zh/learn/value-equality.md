# 26 值相等

值按内容比较，而不是按构造次数比较。

<<< ../../../norm/tests/docs/tour/value_equality.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/value_equality.out{text}

前两个分别构造的位置字段相同，因此相等。第三个仅列数不同就不相等；嵌套的值字段也按内容参与比较。

动手试试：把 `other.column` 改为四，预测两次比较。

详细规则：[参考](/zh/spec/value-identity-semantics)。
