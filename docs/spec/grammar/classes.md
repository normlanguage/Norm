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

class 可以声明字段、构造器、方法和访问器。非空字段必须在字段初始化式或每条构造路径中完成初始化。构造器名与 class 相同，不写返回类型。

## 继承

class 最多直接继承一个 class，并可实现多个 interface。父构造调用必须在子构造器中显式写出。public 方法参与动态分派，private 方法不参与覆盖。

```norm
class TimedCounter extends Counter implements Printable {
    Instant updatedAt

    TimedCounter(Integer initial, Instant now) {
        super(initial: initial)
        updatedAt = now
    }
}
```

class 赋值、传参和返回保持动态类型与对象身份。`copy()` 创建新的顶层对象身份；具体字段复制规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。class 最多直接继承一个 class，并可实现多个 interface。

