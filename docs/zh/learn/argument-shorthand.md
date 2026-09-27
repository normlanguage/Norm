# 10 同名实参简写

局部变量名与参数名相同时，可以省去参数标签。

<<< ../../../norm/tests/docs/tour/argument_shorthand.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/argument_shorthand.out{text}

`remaining(total, completed)` 的两个变量与相同位置的参数同名，等价于 `remaining(total: total, completed: completed)`。第二次调用展示完整写法。名称不同的裸值不能使用这种简写。

动手试试：把局部变量 `completed` 改名为 `done`，改用显式标签使调用继续有效。

详细规则：[语言参考](/zh/spec/grammar/functions)。
