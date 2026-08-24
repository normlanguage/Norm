# Package 系统

package 是 Norm 源码的命名空间。一个源码根目录可以包含多个 package，同一 package 可以由多个源码文件共同组成；声明顺序和文件名不参与名称解析。

```norm
package geometry

public class Point {
    Integer x
    Integer y
}

public Integer area(Integer width, Integer height) {
    return width * height
}
```

## 文件与源码根

package 名由点分隔的标识符组成，并与源码根目录下的相对目录一致。`package geometry.shapes` 的源码位于 `<source-root>/geometry/shapes/`。

文件名不创建命名空间，也不限制文件中的 public 声明数量。项目可以按主要类型命名文件，但这只是组织约定。跨文件名称解析只发生在 `module.norm` 建立的 source set 内；不存在清单时，无论入口是否声明 package，都按独立单文件处理。

## 可见性

- 顶层声明默认 `public`，可以被同 package 文件直接使用；声明所在源文件被模块导出后，也可以被其他 package 导入；
- `private` 顶层声明只在当前源码文件可见；
- Norm 不提供 package-private 或 `protected`；
- public 签名不能暴露 private 类型。

## 编译边界

编译器先收集项目中已加载源码的全部声明签名，再解析 import 和函数体，因此不同文件可以互相引用，也可以形成函数递归或纯声明依赖环。源码不包含顶层可变初始化，名称解析不依赖文件顺序。

package 负责源码名称空间，`module.norm` 负责 source set 与模块边界。模块内同 package 文件可以直接引用彼此；跨 package 可见范围由 `exports` 精确决定。参见[模块系统](/spec/module-system)。
