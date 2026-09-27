# 01 程序入口

独立的 `.norm` 文件从顶层 `main()` 函数开始运行。

<<< ../../../norm/tests/docs/tour/01_hello.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/01_hello.out{text}

花括号中的语句在程序启动时执行。`printLine` 向标准输出写入一行。这里的 `main()` 没有声明结果类型，因此是 `Void` 入口。整个程序只需要这一个源码文件。

把上面的代码保存为 `hello.norm`，再使用正式发行版 CLI 在该目录运行 `norm hello.norm`。

动手试试：把问候文字改成你的名字，保存并重新运行。

详细规则：[语言参考](/zh/spec/grammar/declarations)。

下一节：[显式类型与赋值](/zh/learn/bindings)。
