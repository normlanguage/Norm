# 12 末尾表达式返回

有结果类型的函数可以省略 `return`，直接使用末尾表达式。

<<< ../../../norm/tests/docs/tour/final_expression.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/final_expression.out{text}

`square` 返回 `value * value`；`sumOfSquares` 组合两次调用并返回和。两个函数的签名仍明确写出 `Integer`，末尾表达式必须与它兼容。这是返回规则，不改变参数传递方式。

动手试试：把 `square` 的末尾表达式改为字符串，观察类型错误。

详细规则：[语言参考](/zh/spec/grammar/functions)。
