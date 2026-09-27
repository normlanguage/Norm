# 74 函数拦截

`FunctionInterceptor` 包裹声明侧的 `greet` 调用。`around` 调用一次 `proceed()`，因此原函数仍产生问候文本。

<<< ../../../norm/tests/docs/tour/function_interception.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/function_interception.out{text}

动手试试：删除 `proceed()`，观察必须如何提供返回值。

精确规则：[参考](/zh/spec/annotations)。
