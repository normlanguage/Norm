# 72 注解保留策略

`CompileHint` 使用源码保留，`CatalogHint` 使用二进制保留。运行时注解查询都不会返回它们。二进制元数据供编译器和文档工具使用；运行时反射则需要 `RuntimeRetention`。

<<< ../../../norm/tests/docs/tour/annotation_retention.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/annotation_retention.out{text}

动手试试：把 `CatalogHint` 改为 `RuntimeRetention`，预测第二行输出。

精确规则：[参考](/zh/spec/annotations)。
