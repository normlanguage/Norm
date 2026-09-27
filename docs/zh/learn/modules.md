# 61 模块

`module.norm` 声明模块名、版本和导出源文件。运行模块内入口文件时，CLI 会发现并读取此描述。`package app` 为入口文件指定顶层包名；后续课程再引入嵌套包。

入口文件 `app/Main.norm`：

<<< ../../../norm/tests/docs/projects/modules/app/Main.norm{norm}

模块描述 `app/module.norm`：

<<< ../../../norm/tests/docs/projects/modules/app/module.norm{norm}

从仓库根目录运行：

```sh
norm run norm/tests/docs/projects/modules/app/Main.norm
```

预期输出：

<<< ../../../norm/tests/docs/projects/modules/expected.out{text}

动手试试：只修改模块名称而不重命名目录，观察名称不匹配诊断。再将模块目录改为相同名称、更新运行路径并重试。

精确规则：[语言参考](/zh/spec/module-system)。
