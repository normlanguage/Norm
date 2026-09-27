# 41 嵌套模式

模式可以匹配另一个变体负载中的变体。

<<< ../../../norm/tests/docs/tour/nested_patterns.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/nested_patterns.out{text}

第一个分支仅在 Updated 事件包含 Sent 时取出编码。第二个分支处理其他 Updated 配送状态，最后处理 Ignored；各分支按顺序匹配，不会贯穿执行。

动手试试：交换前两个 case，观察编译器是否报告不可达分支。

详细规则：[参考](/zh/spec/grammar/patterns)。
