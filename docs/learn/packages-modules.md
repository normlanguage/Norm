# 12 Package 与 Module

Package 组织公开名称，Module 定义源码根、导出边界和精确依赖图。

## 源文件

业务入口可以放在 package 中：

<<< ../../norm/tests/docs/projects/packages/app/Main.norm{norm}

`package` 必须是文件的第一个声明，并与源码根下的相对目录一致。`import` 位于 package 之后，只导入 public 名称；`as` 为当前文件建立局部别名。`private` 始终限制在声明文件内。

没有 package 声明的文件是独立脚本。脚本不能导入项目源码，也不能被项目源码导入。

## Module 配置

模块根目录中的 `module.norm` 是普通 Norm 源文件，并提供唯一的零参数模块工厂：

<<< ../../norm/tests/docs/projects/packages/app/module.norm{norm}

模块名与版本参与公开名义类型身份。`exports` 声明跨 package 可见的源码；跨模块 import 还要求目标是当前模块的直接依赖。传递依赖不会自动获得可见性。

CLI、Language Server 和测试工具读取同一份模块描述和 source set。完整路径、依赖和可见性规则见[模块系统](/spec/module-system)。

上一章：[Annotation](/learn/annotations)。接下来可以按需查阅 [Language Reference](/spec/language-spec)、[Standard Library](/stdlib/overview)和[当前状态](/status)。
