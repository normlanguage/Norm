# 05 运算与比较

算术运算得到数值；比较和布尔运算得到 `Boolean`。

<<< ../../../norm/tests/docs/tour/operators.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/operators.out{text}

乘法优先于加法，因此总额是 `8 * 3 + 2 = 26`。`<=` 和 `>` 得到布尔结果。`&&` 组合两个布尔值；左侧为 false 时不求值右侧。

动手试试：把 `quantity` 改为 `0`，先预测三行输出，再运行。

详细规则：[语言参考](/zh/spec/grammar/operators-precedence)。
