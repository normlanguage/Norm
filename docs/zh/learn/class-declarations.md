# 27 类声明

`class` 用字段和行为表示可以变化的实体。

<<< ../../../norm/tests/docs/tour/class_declarations.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/class_declarations.out{text}

带标签的构造为 `value` 赋初值。调用 `increment()` 修改同一个计数器，随后读取字段得到一。方法让状态变化靠近其所属实体。

动手试试：调用两次 `increment()`，预测结果。

详细规则：[参考](/zh/spec/grammar/classes)。
