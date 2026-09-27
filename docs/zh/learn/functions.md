# 07 函数与显式返回

具名函数让计算拥有可复用的输入和结果。

<<< ../../../norm/tests/docs/tour/03_functions.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/03_functions.out{text}

`double` 声明一个 `Integer` 参数和一个 `Integer` 结果。`return` 把结果交还给调用方。只有一个实参的调用可以省略标签。两次调用复用同一个函数体，但传入不同的值。

动手试试：把乘法改成加法，预测两行输出。

详细规则：[语言参考](/zh/spec/grammar/functions)。
