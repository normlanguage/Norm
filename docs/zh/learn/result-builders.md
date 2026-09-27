# 82 结果构建器

`@BuildWith(Words.class)` 把回调中的表达式语句变成依次执行的 `add` 调用。`finish()` 返回累积的 `String`；每次执行回调都使用新的构建器。

<<< ../../../norm/tests/docs/tour/result_builders.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/result_builders.out{text}

动手试试：在代码块中增加一个字符串表达式。

精确规则：[参考](/zh/spec/grammar/result-builders)。
