# 导入系统

`import` 把其他 package 的公开名称引入当前文件。导入只影响名称解析，不执行代码，也不会改变可见性。

```norm
package drawing

import geometry.Point
import geometry.area

Point origin = Point(x: 0, y: 0)
```

## 规则

- import 必须位于 package 声明之后、其他声明之前。
- 默认导入单个明确名称。
- 两个导入产生相同短名称时，文件必须使用限定名或显式别名。
- 未使用的导入产生警告，不改变程序语义。
- import 路径区分大小写，并与声明的完全限定名一致。
- 跨 package import 的声明必须是 `public`，且声明所在文件必须由其模块导出。

```norm
import geometry.Point as GeometryPoint

GeometryPoint point = GeometryPoint(x: 2, y: 3)
```

通配符导入暂不进入核心语法，以免新增公开声明后静默改变现有文件的名称解析。标准库预导入集合必须很小并由语言版本固定。

模块描述和 import 各司其职：`module.norm` 决定哪些源文件可以跨 package 使用，import 决定当前文件引入其中哪个声明。版本选择不写进 import。
