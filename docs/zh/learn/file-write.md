# 85 写入并读取文件

`CreateNew` 认领新文件名；若文件已存在就失败，不会覆盖。写入器在 `finally` 中关闭，然后才进行限定字节数的 UTF-8 读取。

<<< ../../../norm/tests/docs/libraries/files/write_read.norm{norm}

从 Norm 仓库根目录创建全新的空练习目录：

```sh
mkdir .tmp/norm-file-lesson
cd .tmp/norm-file-lesson
norm run ../../norm/tests/docs/libraries/files/write_read.norm
```

预期输出：

<<< ../../../norm/tests/docs/libraries/files/write_read.out{text}

程序会在练习目录中留下它创建的 `note.txt`，供下一节使用。若不删除就再次写入，打开已存在文件时会失败，原文件不会被覆盖。

动手试试：连续运行两次，检查第二次的错误。

精确规则：[文件系统 API](/zh/stdlib/filesystem)。
