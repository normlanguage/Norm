# 11 `if` 的返回值

`if/else` 表达式可以从两个分支中得到一个值。

<<< ../../../norm/tests/docs/tour/if_results.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/if_results.out{text}

两个分支的末尾表达式都是 `String`，因此整个 `if` 也是 `String`，可以直接返回。需要结果时，每条可正常结束的路径都必须给出兼容的值；缺少 `else` 会留下没有结果的路径。

动手试试：把一个分支改成 `Integer`，观察结果类型如何被检查。

详细规则：[语言参考](/zh/spec/grammar/expressions)。
