# 04 字符串插值

使用 `${expression}` 把有类型的表达式放进字符串。

<<< ../../../norm/tests/docs/tour/interpolation.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/interpolation.out{text}

构造字符串时，花括号中的 `name` 或 `completed` 会求值。整段内容仍是一个 `String` 表达式，嵌入的值会转换成显示文本；`completed` 本身的类型不变。

动手试试：把插值改为 `${completed + 1}`，比较输出。

详细规则：[语言参考](/zh/spec/grammar/lexical)。
