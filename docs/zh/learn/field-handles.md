# 78 字段句柄

`bind(task)` 将有类型的字段描述符与一个类对象结合。`FieldHandle<String>` 可以随后读写该对象的字段，和词法 `ref<T>` 不同。

<<< ../../../norm/tests/docs/tour/field_handles.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/field_handles.out{text}

动手试试：把同一字段绑定到另一个任务，再比较结果。

精确规则：[参考](/zh/spec/declaration-references)。
