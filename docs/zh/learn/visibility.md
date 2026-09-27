# 63 可见性

`heading` 为 public，可导入 `Main.norm`；`prefix` 为 private，仅声明文件可见。调用方应使用公开函数，而非导入内部辅助函数。

入口文件 `app/Main.norm`：

<<< ../../../norm/tests/docs/projects/visibility/app/Main.norm{norm}

被导入文件 `app/tools/Format.norm`：

<<< ../../../norm/tests/docs/projects/visibility/app/tools/Format.norm{norm}

模块描述 `app/module.norm`：

<<< ../../../norm/tests/docs/projects/visibility/app/module.norm{norm}

从仓库根目录运行：

```sh
norm run norm/tests/docs/projects/visibility/app/Main.norm
```

预期输出：

<<< ../../../norm/tests/docs/projects/visibility/expected.out{text}

动手试试：试着在 `Main.norm` 中导入 `prefix`，观察可见性诊断。

精确规则：[语言参考](/zh/spec/module-system)。
