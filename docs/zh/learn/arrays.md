# 18 数组

数组和列表一样可按索引访问，但长度固定。

<<< ../../../norm/tests/docs/tour/arrays.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/arrays.out{text}

`Array<Integer>` 让字面量成为数组。给 `scores[1]` 赋值会改变该元素，`size()` 仍为三；若需要增减元素，应使用列表。

动手试试：替换第一个元素并再次输出。

详细规则：[参考](/zh/spec/grammar/literals)。
