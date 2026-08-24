# Package 语法

```text
PackageDeclaration := "package" Identifier ("." Identifier)*
```

package 声明是项目源码文件的第一个声明，名称使用点分隔，不以分号结尾。

```norm
package geometry.shapes

class Circle {
    Integer radius
}
```

一个文件最多声明一个 package。package 名必须与源码根下的相对目录一致。多个文件可以声明同一 package，其 public 名称共同构成 package API。

没有 package 声明的文件是单文件脚本。脚本不能 import 项目源码，也不能被项目源码 import。

重复 public 名称、package 名与 import 别名冲突、路径与 package 不一致都属于编译错误。
