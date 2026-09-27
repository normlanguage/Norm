# 60 导入

`import` 让当前文件能以短名称使用其他包中的公开声明。这里的 `abs` 来自标准库 `std.math`；这个单文件程序不需要配置项目。

<<< ../../../norm/tests/docs/tour/imports.norm{norm}

从仓库根目录运行：

```sh
norm run norm/tests/docs/tour/imports.norm
```

预期输出：

<<< ../../../norm/tests/docs/tour/imports.out{text}

动手试试：删除导入语句，观察未解析名称的错误。

精确规则：[导入系统](/zh/spec/import-system)。
