# 75 拦截器的进入与退出

`before` 在函数体之前执行，`after` 在调用结束时执行。`completion.succeeded()` 指出被拦截的调用是否正常完成。

<<< ../../../norm/tests/docs/tour/interceptor_order.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/interceptor_order.out{text}

动手试试：让 `greet` 抛出异常，观察完成状态。

精确规则：[参考](/zh/spec/annotations)。
