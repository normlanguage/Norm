# 86 类型化的文件错误

上一节创建 `note.txt` 后，只删除这个由练习创建的文件。再次读取同一路径会抛出 `FileException`，其有类型的 `reason` 指出 `NotFound`。

<<< ../../../norm/tests/docs/libraries/files/missing.norm{norm}

若还在第 85 节的练习目录中，运行：

```sh
rm note.txt
norm run ../../norm/tests/docs/libraries/files/missing.norm
cd ../..
```

也可以在任意全新的空练习目录中独立运行此源文件。不要删除本练习之外的文件。

预期输出：

<<< ../../../norm/tests/docs/libraries/files/missing.out{text}

动手试试：删除 `note.txt` 之前先运行读取程序，观察异常分支不会执行。

精确规则：[文件系统错误](/zh/stdlib/filesystem#errors)。
