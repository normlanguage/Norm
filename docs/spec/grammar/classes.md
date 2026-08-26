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

class 可以声明字段、一个显式构造器和方法。构造器名与 class 相同，不写返回类型和可见性修饰符；没有显式构造器的根 class 使用字段标签构造。显式构造器的每条正常退出路径必须初始化本 class 声明的全部字段。

## 继承

class 最多直接继承一个 class，并可实现多个 interface。子 class 必须声明构造器，并把 `super(...)` 写成构造器中的第一项。父字段先由父构造器初始化，子构造器只初始化本 class 声明的字段。字段不能隐藏继承字段。

```norm
class TimedCounter extends Counter implements Printable {
    Instant updatedAt

    TimedCounter(Integer initial, Instant now) {
        super(initial: initial)
        updatedAt = now
    }
}
```

public 方法按参数标签、参数类型和泛型形状覆盖，并参与 class 与 interface 调用的动态分派；返回类型可以协变。private 方法不继承也不参与覆盖。

class 赋值、传参和返回保持动态类型与对象身份。`copy()` 创建新的顶层对象身份；具体字段复制规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。
