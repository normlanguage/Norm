# 模块系统

模块是一个源码根及其跨 package 公开边界。配置固定在 `<source-root>/<module-name-path>/module.norm`，该目录就是模块的根 package。应用入口 `Main.norm` 与配置同目录；子 package 位于其下。`module.norm` 是普通 Norm 源文件，并提供唯一的零参数模块工厂：

```norm
import std.math.max

Module module() {
  return module(
    name: "sample",
    version: max(left: 1, right: 1),
    exports: ["Main", "model.User"]
  )
}
```

项目启动时，工具链生成隐藏入口并调用 `Module module()`，再建立业务源码的 `ProjectSourceSet` 并调用业务 `main()`。模块配置和业务程序分别形成 Core artifact；配置文件不进入业务 source set，也不成为业务 `main` 的 Core 依赖。

`Module`、`ModuleRequirement`、`module(...)` 和 `dependency(...)` 由 bootstrap 源码定义。参数化的 `module(...)` 是返回 `Module` 实现的普通 Norm 工厂，零参数 `module()` 是用户入口，二者按函数重载解析。模块配置可以声明普通类型与函数、实现自己的 `Module`，也可以导入标准库。

`name` 是点分隔的模块名，也是模块内 package 的共同前缀。工具链 prelude 使用的 `std` 与 `norm.bootstrap` 是保留模块名。`version` 是正整数模块版本，并参与公开名义类型的稳定身份。`exports` 是不重复的相对源码名。`dependencies` 是精确的模块名与版本坐标：

```norm
Module module() {
  return module(
    name: "app",
    version: 1,
    exports: ["Main"],
    dependencies: [dependency(name: "base", version: 2)]
  )
}
```

本地依赖位于 `<project-root>/dependencies/<module-name-path>/module.norm`，点分隔模块名按目录展开。同一坐标只从项目依赖仓库解析一次；工具链递归求值依赖配置，校验返回坐标与声明完全一致，拒绝依赖环、同名模块的多版本选择，以及由多个模块共同拥有同一 package 的 split package。

## 源文件映射

项目系统把模块名和导出名连接后，将点替换为目录分隔符并添加 `.norm`。模块 `std` 的 `collections.sequences` 映射为：

```text
std/collections/sequences.norm
```

该文件必须声明 `package std.collections`。文件名不参与 package 名，但用于确定导出的具体源码文件。

## Source set

存在根模块配置时，source set 包含根模块及其依赖图中的业务 `.norm` 源码，排除所有配置文件和未声明的嵌套模块。每个业务源码的相对目录必须与其 package 一一对应，并位于所属模块名的 package 前缀下。带 package 声明且位于 package 目录内的同名文件是普通业务源码。

Language Server 合并未保存内容后执行同一项目加载生命周期，因此编辑器、CLI 和测试工具读取一致的模块描述和 source set。没有根模块配置时，入口按独立单文件编译单元处理。

## 可见范围

同一模块、同一 package 的源码文件自动加载并可以直接引用彼此的 `public` 声明。跨 package import 只能访问 `exports` 指定文件中的 `public` 声明；跨模块 import 还要求目标模块是当前模块的直接依赖，传递依赖不会自动获得可见性。`private` 始终限制在声明文件内。标准库是工具链显式加入每个模块读取边界的隐式依赖。

标准库首先使用 module bootstrap 求值自己的 `module.norm`，再成为用户模块配置和业务程序共同使用的 prelude。项目生命周期的实现入口见 `cli/compiler` 的 `project` package，bootstrap 协议的单一实现见 `cli/compiler/src/main/resources/bootstrap/module.norm`。
