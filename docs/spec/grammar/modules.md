# Module 配置

模块根 package 目录中的 `module.norm` 是普通 Norm 源文件，使用同一套 import、声明、表达式和函数体语法。它不声明 package，并必须提供：

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

依赖使用 `List<ModuleRequirement>` 表示，并通过 `dependency(String name, Integer version)` 构造。完整语义见[模块系统](/spec/module-system)。
