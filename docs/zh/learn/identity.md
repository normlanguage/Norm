# 28 类的身份

类变量可以指向同一个实体，也可以指向字段看似相同的不同实体。

<<< ../../../norm/tests/docs/tour/identity.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/identity.out{text}

`alias` 和 `first` 指向同一对象，因此通过别名写入会把 `first.value` 改为三。另行构造的对象字段也为三，但身份不同。

动手试试：改为 `equalFields = first`，预测最后一次比较。

详细规则：[参考](/zh/spec/value-identity-semantics)。
