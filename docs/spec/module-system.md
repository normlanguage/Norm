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

`name` 是点分隔的模块名，也是模块内 package 的共同前缀。`version` 是正整数模块描述格式版本。`exports` 是不重复的相对源码名。

## 源文件映射

编译器把模块名和导出名连接后，将点替换为目录分隔符并添加 `.norm`。模块 `std` 的 `collections.sequences` 映射为：

```text
std/collections/sequences.norm
```

该文件必须声明 `package std.collections`。文件名不参与 package 名，但用于确定导出的具体源码文件。

## 可见范围

同 package 的源码文件自动加载并可以直接引用彼此的 `public` 声明。跨 package import 只能访问 `exports` 指定文件中的 `public` 声明；`private` 始终限制在声明文件内。

没有 `module.norm` 时，带 package 的入口只加载入口 package 目录中的源码。无 package 文件保持单文件脚本语义。

`module.norm` 不在程序运行时执行。CLI、编译器、标准库加载器和语言服务使用同一个模块描述模型。
