# 函数

函数是 Norm 的顶层语言结构，不需要放进 class。class 用于描述对象，模块用于组织代码，函数用于表达行为。

## 声明函数

```norm
int square(int value) {
    return value * value
}
```

声明顺序是：返回类型、函数名、参数列表、函数体。没有返回值的函数使用 `void`。

```norm
void printLine(String text) {
    print(text)
}
```

Norm 不使用隐式的最后表达式返回值。返回函数结果必须写 `return`。

## 参数与命名调用

```norm
int clamp(int value, int minimum, int maximum) {
    if value < minimum {
        return minimum
    }
    if value > maximum {
        return maximum
    }
    return value
}
```

具有多个参数的函数默认使用命名调用：

```norm
int opacity = clamp(value = 140, minimum = 0, maximum = 100)
```

参数名是公开调用约定的一部分。命名调用可以避免连续出现多个同类型实参时产生含义歧义。

## 顶层函数

不依赖实例状态的行为直接放在模块顶层：

```norm
double average(double left, double right) {
    return (left + right) / 2.0
}
```

Norm 没有 `static`，因此不需要创建 `MathUtils` 一类只充当函数容器的 class。

## 方法

方法是声明在 class 中、能够访问实例状态的函数。

```norm
class Accumulator {
    int total

    void add(int amount) {
        total = total + amount
    }

    int current() {
        return total
    }
}
```

方法调用使用点号：

```norm
Accumulator sum = Accumulator(total = 0)
sum.add(amount = 4)
```

## 函数类型

函数可以作为值传递。函数类型保留返回类型、函数名位置和参数类型：

```norm
int apply(int operation(int value), int input) {
    return operation(input)
}

int doubled = apply(operation = square, input = 2)
```

参数位置上的 `operation` 是局部名称；`int operation(int value)` 描述它能接收一个 `int` 并返回一个 `int`。

## 匿名函数

匿名函数与普通函数具有相同形状，只是省略名称：

```norm
int incremented = apply(
    operation = int(int value) {
        return value + 1
    },
    input = 4
)
```

匿名函数不能任意捕获外层局部变量，因此不会形成隐藏 closure 环境。需要上下文时，把它作为显式参数传入，或者使用绑定方法引用。

```norm
apply(operation = transformer.apply, input = 4)
```

## 重载与覆盖

参数名称或类型至少有一项不同时，可以形成重载。任何导致调用点无法唯一确定目标函数的声明都非法。

public 实例方法默认可以被子类覆盖；private 方法不参与覆盖。Norm 不增加 `final`、`open`、`virtual` 或 `override` 关键字。

下一章：[控制流](/language/control-flow)。

