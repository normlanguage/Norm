# 71 注解目标

`Label` 实现 `TypeTarget`，因此可标记类型声明。`SourceRetention` 让此注解在编译期间发挥作用，不提供运行时反射。

<<< ../../../norm/tests/docs/tour/annotation_targets.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/annotation_targets.out{text}

动手试试：将 `@Label` 移到 `main()` 上，观察目标不匹配的诊断。

精确规则：[参考](/zh/spec/annotations)。
