# 08 命名参数

调用多参数函数时，用参数名标明每个值的位置。

<<< ../../../norm/tests/docs/tour/named_arguments.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/named_arguments.out{text}

`left: 9, right: 4` 计算 `9 - 4`。第二次调用只调换书写顺序，保持各标签对应的值，所以结果仍为 `5`。第三次改变各标签收到的值，结果变为 `-5`。标签决定实参与形参的绑定；源码顺序决定实参表达式的求值顺序。

动手试试：让两个实参分别调用会打印一行的函数，观察求值顺序。

详细规则：[语言参考](/zh/spec/grammar/functions)。
