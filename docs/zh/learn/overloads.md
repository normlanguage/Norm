# 51 函数重载

两个声明都叫 `adjust`，但参数类型决定选用哪个方法体。编译器根据可用签名检查调用。

<<< ../../../norm/tests/docs/tour/overloads.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/overloads.out{text}

动手试试：添加一个 `Boolean` 重载并调用。

精确规则：[语言参考](/zh/spec/grammar/functions)。
