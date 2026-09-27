# 09 默认参数

参数带默认值时，调用方可以省略对应标签。

<<< ../../../norm/tests/docs/tour/default_arguments.norm{norm}

输出：

<<< ../../../norm/tests/docs/tour/default_arguments.out{text}

第一次调用省略 `punctuation`，于是使用默认的 `"!"`；第二次显式提供 `"?"`。函数体两次得到的都是 `String`，默认值属于函数声明的调用契约。

动手试试：把默认值改为 `"."`，观察哪一次调用的输出改变。

详细规则：[语言参考](/zh/spec/grammar/functions)。
