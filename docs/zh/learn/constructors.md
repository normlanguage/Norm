# 30 显式构造函数

类可以用同名的显式构造函数取代隐式字段构造。

<<< ../../../norm/tests/docs/tour/constructors.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/constructors.out{text}

`Counter(Integer initial)` 没有返回类型。构造函数必须在对象可用前给必需字段赋值，调用时给 `initial` 参数写标签。

动手试试：在构造函数中把 `initial` 加倍，预测字段值。

详细规则：[参考](/zh/spec/grammar/classes)。
