# Module 语法

```text
ModuleDeclaration := "module" Identifier ("." Identifier)*
```

module 声明是源码文件第一个非注释声明。名称使用点分隔，不以分号结尾。

```norm
module geometry.shapes

public value Circle {
    double radius
}
```

## 文件关系

多个文件可以声明同一模块，其 public 名称组成模块 API。声明顺序和文件名不参与名称解析。一个文件只能属于一个模块。

## 顶层声明

模块可以直接包含类型、函数和编译期常量。当前草案不允许任意顶层执行语句或依赖文件顺序的可变初始化。

模块名称与包管理器中的发布包不同：包负责版本与分发，模块负责源码名称空间。规范模块名不能包含版本号。

重复 public 名称、模块名与导入别名冲突、同一文件多次 module 声明都属于编译错误。

