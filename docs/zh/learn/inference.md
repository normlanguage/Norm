# 03 类型推断

`var` 让初始化表达式决定局部变量的静态类型。

<<< ../../../norm/tests/docs/tour/inference.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/inference.out{text}

`count` 被推断为 `Integer`，`title` 被推断为 `String`。再次赋值仍受原类型检查；`var` 不会让变量变成动态类型。`null` 和空列表 `[]` 没有足够的类型信息，不能单独作为 `var` 的初始化器。

动手试试：试着把 `count = count + 1` 改成赋予字符串，观察诊断。

详细规则：[语言参考](/zh/spec/type-inference)。
