# Interface 声明

interface 是 Norm 唯一的名义行为抽象机制，不保存实例字段。标准库所称 protocol 是承担通用协议角色的普通 interface，不是另一种声明或匹配机制。

```norm
interface Formatter<T> {
    String format(T value)
}

class PointFormatter implements Formatter<Point> {
    public String format(Point value) {
        return "point"
    }
}
```

## 规则

- 类型只有显式写 `implements` 才满足 interface；同名方法不会结构化匹配。
- interface 可以通过 `extends` 扩展多个 interface，但继承图不能成环。
- 实现方法的参数类型、返回类型和可见性必须满足契约。
- interface 不改变数据类别：class 通过 interface 传递仍保留对象 identity，value 仍遵循 value 语义。

```norm
interface Ordered<T> extends Comparable<T>, Equatable<T> {
}
```

初版 interface 方法只声明签名，不提供默认实现。通过 interface 调用方法时，运行时按值的具体名义类型选择其显式实现。

运行时类型检查 `value is InterfaceName` 使用声明关系，不检查成员形状。
