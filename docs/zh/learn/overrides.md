# 46 重写方法

`Friendly` 提供新的 `text()` 方法体。第一次调用虽通过 `Greeting` 类型变量发起，仍会派发到 `Friendly` 的实现。

<<< ../../../norm/tests/docs/tour/overrides.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/overrides.out{text}

动手试试：把第一次赋值中的 `Friendly()` 换成 `Greeting()`。

精确规则：[语言参考](/zh/spec/grammar/classes)。
