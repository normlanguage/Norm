# Norm 测试规范

本目录保存从用户视角执行的 `.norm` 测试。测试按稳定领域组织，不按发布版本组织。

## 分类

单文件测试直接放入最主要的语言领域目录，例如 `class`、`value`、`references`、`exceptions`、`reflection` 和 `annotations`。

其他目录职责：

- `base`：不属于独立语言领域的基础语义。
- `algorithms`：使用 Norm 实现的算法程序；允许按题集建立子目录。
- `projects`：跨 package、跨 module 或带依赖的多文件程序。
- `stdlib`：标准库的用户级验收程序。
- `recovery`：编辑器和语法恢复使用的不完整源码夹具，不作为可执行程序。

新增或迁移测试时遵守以下边界：

- 按被测主语选择唯一领域，不为同一能力建立第二套目录。
- 版本号、里程碑和 `conformance` 不进入路径。
- 目录名不重复上层已经表达的信息，例如 `projects` 下不使用 `cross_package_` 前缀。
- 文件名使用能独立表达行为的 `snake_case` 名称；编号只有在编号本身属于用例身份时使用。
- 编译诊断的单元测试放在产生诊断的 Java 模块中；这里只保存真实 `.norm` 程序和专用恢复夹具。

## 单文件程序

可执行程序必须：

- 只包含一个 `main` 入口。
- 直接验证一个清晰的行为边界。
- 使用 `std.testing.expectedOutputLine` 或 `expectedOutputLines` 声明非空预期输出。
- 能独立编译和执行，不依赖其他测试文件的声明或执行顺序。

单文件目录由 [`ProgramExecutionTest`](../../compiler/src/test/java/dev/w0fv1/norm/truffle/ProgramExecutionTest.java) 注册，并由 [`NormTestKit`](../../compiler/src/test/java/dev/w0fv1/norm/testing/NormTestKit.java) 递归发现。新增顶层领域时必须同时增加对应的测试入口；领域内新增文件无需注册。

## 项目程序

每个项目场景使用以下结构：

```text
projects/<scenario>/
├── app/
│   ├── module.norm
│   └── ...
└── dependencies/
    └── <module>/
        ├── module.norm
        └── ...
```

其中：

- `<scenario>` 使用被验证事实的 `snake_case` 名称。
- `app/module.norm` 是唯一执行根，module 名固定为 `app`。
- `app` 中必须恰好存在一个 `main` 入口。
- `dependencies` 仅在验证真实模块依赖时创建，其中的模块不会被当作独立项目执行。
- 每个场景拥有自己的源码根和依赖目录，不跨场景共享夹具。

`projects` 的一级目录由 `NormTestKit.projectSuite` 自动发现，新增场景无需修改 Java 注册代码。

## 验证

运行 `ProgramExecutionTest` 覆盖的全部语言与项目程序：

```powershell
.\gradlew.bat :compiler:test --tests "dev.w0fv1.norm.truffle.ProgramExecutionTest"
```

运行项目程序：

```powershell
.\gradlew.bat :compiler:test --tests "dev.w0fv1.norm.truffle.ProgramExecutionTest.runsMultiFilePrograms"
```

运行单个领域时，使用 `ProgramExecutionTest` 中对应的测试工厂方法。标准库程序由 `StandardLibraryTest` 运行。

测试架构和工具链约束见 [`docs/design/toolchain-development.md`](../../docs/design/toolchain-development.md)。
