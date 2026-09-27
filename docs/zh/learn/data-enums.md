# 38 带数据的枚举

枚举的不同选项可以携带不同类型的数据。

<<< ../../../norm/tests/docs/tour/data_enums.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/data_enums.out{text}

`Success` 携带整数数量，`Failure` 携带字符串原因。构造时给负载字段写标签，每个值都保留自己属于哪个变体。

动手试试：用不同数量再构造一个 `Success` 并比较。

详细规则：[参考](/zh/spec/enum-design)。
