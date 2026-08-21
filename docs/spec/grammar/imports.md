# Import 语法

```text
Import := "import" QualifiedName ("as" Identifier)?
```

import 位于 module 声明之后、其他声明之前，并且只导入 public 名称。

```norm
module drawing

import geometry.Point
import geometry.render as renderPoint
```

## 名称解析

无别名时，最后一个名称段成为文件内短名称。局部声明优先于导入名称，但产生遮蔽时编译器应给出警告。两个 import 产生相同短名称是错误，除非至少一个使用别名。

导入不具有传递性：模块 A 导入 B，不会让 A 的使用者自动看到 B。导入也不运行初始化代码。

当前核心语法没有 wildcard import。标准预导入仅包含基本类型和极少量核心函数，并由语言版本固定。完整模块边界见[导入系统](/spec/import-system)。

