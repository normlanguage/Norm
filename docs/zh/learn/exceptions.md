# 66 异常处理

`throw` 中断正常执行；匹配的 `catch Exception error` 处理异常后，程序继续执行下一条语句。

<<< ../../../norm/tests/docs/tour/exceptions.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/exceptions.out{text}

动手试试：删除 catch，观察未处理异常的行为。

精确规则：[参考](/zh/spec/error-model)。
