# 73 运行时注解元数据

具有运行时保留策略的 `Label` 可从 `Task.class` 读取。查询返回可空的 `Label?`，因此示例使用安全访问和回退值。

<<< ../../../norm/tests/docs/tour/runtime_annotations.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/runtime_annotations.out{text}

动手试试：删除 `@Label`，观察回退结果。

精确规则：[参考](/zh/spec/annotations)。
