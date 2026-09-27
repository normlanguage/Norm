# 25 值声明

`value` 把不可变字段组合成带类型的数据值。

<<< ../../../norm/tests/docs/tour/value_declarations.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/value_declarations.out{text}

构造时按字段名给值。构造完成后不能再给字段赋值；坐标变化时应建立新值。因此位置描述数据，而不是会变化的实体。

动手试试：构造第二个位置，让列数不同。

详细规则：[参考](/zh/spec/grammar/values)。
