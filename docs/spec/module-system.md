# 模块系统

模块是一个源码根及其跨 package 公开边界。源码根目录中的 `module.norm` 包含一个编译期 `Module` 对象：

```norm
Module(
  name: "std",
  version: 1,
  exports: [
    "collections.sequences",
    "math.integer"
  ]
)
```

`name` 是点分隔的模块名，也是模块内 package 的共同前缀。`version` 是正整数模块版本，并参与公开名义类型的稳定身份。`exports` 是不重复的相对源码名。

## 源文件映射

编译器把模块名和导出名连接后，将点替换为目录分隔符并添加 `.norm`。模块 `std` 的 `collections.sequences` 映射为：

```text
std/collections/sequences.norm
```

该文件必须声明 `package std.collections`。文件名不参与 package 名，但用于确定导出的具体源码文件。

## Source set

存在清单时，source set 包含模块根下的全部 `.norm` 源码，根 `module.norm` 除外；嵌套模块及其源码属于独立 source set。每个源码的相对目录必须与其 package 一一对应，并位于模块名的 package 前缀下。带 package 声明的 `module.norm` 是普通源码文件。

Language Server 先合并未保存的文件内容，再定位清单、嵌套模块和 package 边界，因此编辑器与 CLI 使用同一套项目规则。

## 可见范围

同 package 的源码文件自动加载并可以直接引用彼此的 `public` 声明。跨 package import 只能访问 `exports` 指定文件中的 `public` 声明；`private` 始终限制在声明文件内。

不存在清单时，入口始终作为独立单文件编译单元；package 声明只提供该文件内的命名空间，不会隐式加载相邻文件。

清单不在程序运行时执行。CLI、编译器、标准库加载器、测试工具和语言服务使用同一个 `ProjectSourceSet` 与模块描述模型。
