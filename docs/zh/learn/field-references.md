# 69 字段声明引用

`Task.title.field` 指向字段声明，不绑定某一个对象。`read` 和 `write` 接收具体 `Task` 实例，并保留字段的 `String` 类型。

<<< ../../../norm/tests/docs/tour/field_references.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/field_references.out{text}

动手试试：创建第二个任务，用同一字段描述符读取其标题。

精确规则：[参考](/zh/spec/declaration-references)。
