# 模块系统

模块是一个源码根及其跨 package 公开边界。模块身份由唯一的零参数 `Module module()` 声明决定，而不是由文件名决定。目录项目通常把它放在 `<source-root>/<module-name-path>/module.norm`，并把应用入口放在同目录的 `application.norm`；单文件应用可以在任意 `.norm` 文件中同时声明模块、业务代码和应用入口：

```norm
import std.math.max

Module module() {
  return module(
    name: "sample",
    version: max(left: 1, right: 1),
    exports: ["model.User"]
  )
}
```

项目启动时，工具链先隔离求值 `Module module()`，再建立业务源码的 `ProjectSourceSet`。存在零参数 `Application application()` 且不存在显式 `Void main()` 时，工具链生成同 package 的隐藏入口并调用 `application().run()`；返回对象的静态类型必须提供 `Void run()`。目录项目使用 `norm run <module-directory>`，单文件应用使用 `norm <file.norm>` 或等价的 `norm run <file.norm>`。

`Module`、`ModuleRequirement`、`module(...)`、`dependency(...)` 和 `exportedDependency(...)` 由 bootstrap 源码定义。参数化的 `module(...)` 是返回 `Module` 实现的普通 Norm 工厂，零参数 `module()` 是用户入口。模块配置可以声明普通类型与函数、实现自己的 `Module`，也可以导入标准库。

`name` 是点分隔的模块名，也是模块内 package 的共同前缀。工具链 prelude 使用的 `std` 与 `norm.bootstrap` 是保留模块名。`version` 是正整数模块版本，并参与公开名义类型的稳定身份。`exports` 默认为空，只声明供其他 Module 使用的公开源码；应用自身和模块内部 package 不需要导出入口或实现。`dependencies` 由仓库、模块名和版本组成精确坐标：

```norm
Module module() {
  return module(
    name: "app",
    version: 1,
    exports: ["Main"],
    dependencies: [dependency(repository: "github", name: "base", version: 2)]
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

Language Server 合并未保存内容后执行同一项目加载生命周期，因此编辑器、CLI 和测试工具读取一致的模块描述和 source set。没有相邻 `module.norm` 但当前文件声明 `Module module()` 时，该文件就是模块根；没有模块声明时，入口按独立单文件编译单元处理。

## 可见范围

同一模块、同一 package 的源码文件自动加载并可以直接引用彼此的 `public` 声明；同一模块跨 package 使用显式 import，但不要求 `exports`。跨模块 import 只能访问目标模块 `exports` 指定文件中的 `public` 声明，并要求目标模块是当前模块的直接依赖，或由直接依赖通过 `exportedDependency(...)` 明确导出。普通传递依赖不会获得可见性。组合 Module 使用导出依赖提供稳定的平台边界，应用只声明组合 Module。`private` 始终限制在声明文件内。标准库是工具链显式加入每个模块读取边界的隐式依赖。

标准库首先使用 module bootstrap 求值自己的 `module.norm`，再成为用户模块配置和业务程序共同使用的 prelude。项目生命周期的实现入口见 `cli/compiler` 的 `project` package，bootstrap 协议的单一实现见 `cli/compiler/src/main/resources/bootstrap/module.norm`。
