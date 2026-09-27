# 02 显式类型与赋值

变量声明给值命名，并确定其静态类型。

<<< ../../../norm/tests/docs/tour/02_bindings.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/02_bindings.out{text}

`Integer remaining` 和 `String task` 把类型写在变量名前。局部变量在声明时初始化。随后给 `remaining` 赋值会替换其存储的值，右侧仍必须得到 `Integer`。

动手试试：尝试给 `remaining` 赋一个字符串，阅读编译器的类型诊断。

详细规则：[语言参考](/zh/spec/type-system)。
