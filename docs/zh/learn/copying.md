# 29 复制类对象

需要新的一层类身份时，显式调用 `copy()`。

<<< ../../../norm/tests/docs/tour/copying.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/copying.out{text}

副本最初拥有相同字段值，随后可独立修改。由于类按身份比较，相等结果仍为假。若字段指向其他类对象，除非另行复制，它们仍会共享。

动手试试：加入指向另一个类对象的字段，观察浅复制。

详细规则：[参考](/zh/spec/value-identity-semantics)。
