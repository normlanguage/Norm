# 62 包

文件路径 `model/Task.norm` 与 `package app.model` 对应。`Main.norm` 用 `import app.model.Task` 引用跨包的公开值。

入口文件 `app/Main.norm`：

<<< ../../../norm/tests/docs/projects/packages_steps/app/Main.norm{norm}

被导入文件 `app/model/Task.norm`：

<<< ../../../norm/tests/docs/projects/packages_steps/app/model/Task.norm{norm}

模块描述 `app/module.norm`：

<<< ../../../norm/tests/docs/projects/packages_steps/app/module.norm{norm}

从仓库根目录运行：

```sh
norm run norm/tests/docs/projects/packages_steps/app/Main.norm
```

预期输出：

<<< ../../../norm/tests/docs/projects/packages_steps/expected.out{text}

动手试试：移动 `Task.norm` 而不改包声明，观察路径诊断。

精确规则：[语言参考](/zh/spec/package-system)。
