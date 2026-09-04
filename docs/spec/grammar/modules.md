# Module 配置

模块配置使用普通 Norm 源码，与业务代码共享 import、声明、表达式和函数体语法。目录项目通常在模块根 package 目录中的 `module.norm` 提供配置；单文件应用可以在业务文件中直接提供：

```norm
Module module()
```

常用实现调用 bootstrap 源码中的参数化工厂：

```norm
import std.math.max

String projectName() {
  return "sample"
}

Module module() {
  return module(
    name: projectName(),
    version: max(left: 1, right: 1),
    exports: ["Main", "model.User"]
  )
}
```

依赖使用 `List<ModuleRequirement>` 表示，并通过 `dependency(String repository, String name, Integer? version = null)` 构造。`repository` 是依赖身份的一部分，不能省略；`version` 省略时由仓库解析最新稳定版本。完整语义见[模块系统](/spec/module-system)。
