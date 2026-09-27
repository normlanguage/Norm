# 52 函数值

具名函数可以存入 `Function<Integer(Integer)>` 类型变量后再调用；`apply` 也能把同样的函数值作为参数接收。

<<< ../../../norm/tests/docs/tour/function_values.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/function_values.out{text}

动手试试：不用 `saved` 变量，直接把 `double` 传给 `apply`。

精确规则：[语言参考](/zh/spec/grammar/functions-advanced)。
