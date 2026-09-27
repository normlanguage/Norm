# 31 计算属性

计算属性以字段式语法提供访问器行为。

<<< ../../../norm/tests/docs/tour/computed_properties.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/computed_properties.out{text}

`stored` 是实际存储字段。读取 `value` 调用 `get`，赋值调用 `set(next)`；属性本身不另占存储字段。去掉 setter 后它成为只读属性。

动手试试：让 setter 额外加一，再观察读取结果。

详细规则：[参考](/zh/spec/grammar/classes)。
