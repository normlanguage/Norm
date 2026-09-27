# 21 集合的值语义

给内置集合赋值后，新绑定得到独立的容器值。

<<< ../../../norm/tests/docs/tour/collection_values.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/collection_values.out{text}

两份集合最初相等。向 `changed` 添加元素不会改变仍有两个元素的 `original`；随后内容不同，相等结果变为假。若元素本身是类对象，其身份仍可能共享。

动手试试：也给 `original` 添加同样的第三个元素，再比较相等性。

详细规则：[参考](/zh/spec/value-identity-semantics)。
