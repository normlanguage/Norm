# Interface 声明

interface 定义名义行为契约，不保存实例字段。

```norm
interface Formatter<T> {
    String format(T value)
}

class PointFormatter implements Formatter<Point> {
    String format(Point value) {
        return "(${value.x}, ${value.y})"
    }
}
```

## 规则

- 类型只有显式写 `implements` 才满足 interface；同名方法不会结构化匹配。
- interface 可以扩展多个 interface，但继承图不能成环。
- 实现方法的参数类型、返回类型和可见性必须满足契约。
- interface 不改变数据类别：class 通过 interface 传递仍保留对象 identity，value 仍遵循 value 语义。

interface 方法当前只声明签名，不提供默认实现。这样可以避免多个父接口默认方法的选择规则；未来若加入，必须通过独立语言提案定义冲突解析。

运行时类型检查 `value is InterfaceName` 使用声明关系，不检查成员形状。

