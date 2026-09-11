# Class 声明

class 表示具有稳定身份、可以包含可变状态与行为的对象。

```norm
class Counter {
    Integer value

    Counter(Integer initial) {
        value = initial
    }

    Void increment() {
        value = value + 1
    }
}
```

## 成员

class 可以声明字段、一个显式构造器和方法。构造器名与 class 相同，不写返回类型和可见性修饰符；没有显式构造器的根 class 使用字段标签构造。显式构造器的每条正常退出路径必须初始化本 class 声明的普通字段。

带初始化值的 private 字段是对象内部状态，不进入隐式构造参数；每次创建对象时执行其初始化。公开普通字段仍作为构造输入，默认值允许调用者省略对应参数。没有初始化值的普通字段仍需要构造输入。内部状态字段不影响构造输入中必填参数与默认参数的顺序约束。

实现 `std.annotation.ManagedField` 的注解把 class 字段的初始化责任交给外部管理者。此类字段不进入隐式构造参数，也不要求显式构造器赋值；字段的静态类型保持不变，未初始化时读取会抛出可捕获异常。构造器内仍不能读取尚未初始化的字段。此契约不能应用于 value 字段；构造与注入顺序由管理者保证。

## 计算属性

class 和 value 可以用访问器声明不占用存储字段的属性。getter 必须存在；setter 可省略，省略时属性只读。setter 参数的类型就是属性类型，返回类型固定为 Void。

```norm
class Counter {
    private Integer stored

    Counter(Integer initial) { stored = initial }

    Integer value {
        get { return stored }
        set(next) { stored = next }
    }
}

var counter = Counter(1)
counter.value = 2
printLine(counter.value)
```

访问器遵循普通方法的返回、泛型、闭包和动态分派规则。属性读取调用 getter；赋值先求值接收者、再求值右侧，各一次，然后调用 setter。`private set(next)` 限制外部写入；安全导航不能作为赋值目标。value 的 setter 可以调用外部对象或函数，但仍不能修改 value 自身的存储字段。

计算属性不会加入默认构造参数或字段存储。属性与存储字段不能在本类或继承链上互相隐藏；父类的 private 计算属性不属于继承成员。属性的函数类型结果可以直接调用。当前接口声明语法仍使用方法签名。

实例内部可省略属性接收者的 `this`，例如 `value = value + 1`；同名局部变量和参数优先。该规则也适用于继承属性和闭包中的访问，仍遵循 getter/setter 的可见性。函数类型属性可直接写作 `transform(input)`。

覆盖公开属性必须保持公开可见性。只读属性允许 getter 返回类型协变；覆盖公开可写属性时，属性类型必须保持一致，并显式保留公开 setter。setter 参数名是访问器内部的局部名称，不影响覆盖分派。

## 继承

class 最多直接继承一个 class，并可实现多个 interface。子 class 省略构造器时，保留本类字段构造参数，并自动调用可不传实参的父构造器；父构造器的默认参数遵循普通调用规则。没有可用父构造器时编译报错。显式声明子构造器时，必须把 `super(...)` 写成构造器中的第一项。父字段先由父构造器初始化，子构造器只初始化本 class 声明的字段。字段不能隐藏继承字段。

```norm
class TimedCounter extends Counter implements Printable {
    Instant updatedAt

    TimedCounter(Integer initial, Instant now) {
        super(initial: initial)
        updatedAt = now
    }
}
```

public 方法按参数标签、参数类型和泛型形状覆盖，并参与 class 与 interface 调用的动态分派；返回类型可以协变。private 方法不继承也不参与覆盖。 同名重载从整条继承链收集，子类声明不会隐藏父类的其他重载；已被覆盖的方法只保留最具体的实现候选。普通调用、绑定和未绑定方法引用使用同一候选集合。

带有 `std.annotation.ManagedImplementation` 契约注解的 class 可以声明无方法体的 public 方法，返回类型必须显式声明。仍有未实现方法的类及其子类不能直接构造；子类补全所有继承方法后可以构造。框架实现与 Java 投影见 [Java 库适配](/design/java-library-adapters)。

class 赋值、传参和返回保持动态类型与对象身份。`copy()` 创建新的顶层对象身份；具体字段复制规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。
