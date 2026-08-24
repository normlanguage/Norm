# 函数

函数是 Norm 的顶层语言结构，不需要放进 class。class 用于描述对象，模块用于组织代码，函数用于表达行为。

## 声明函数

```norm
Integer square(Integer value) {
    return value * value
}
```

声明顺序是：可选返回类型、函数名、参数列表、函数体。顶层函数省略返回类型时，其返回类型固定为 `Void`；这不是根据函数体进行的推断。

```norm
log(String text) {
    printLine(text)
}
```

具名函数不使用隐式的最后表达式返回值。返回函数结果必须写 `return`。

## 参数与命名调用

```norm
Integer clamp(Integer value, Integer minimum, Integer maximum) {
    if value < minimum {
        return minimum
    }
    if value > maximum {
        return maximum
    }
    return value
}
```

具有多个参数的函数使用命名调用：

```norm
Integer opacity = clamp(value: 140, minimum: 0, maximum: 100)
```

参数名是公开调用约定的一部分。命名调用可以避免连续出现多个同类型实参时产生含义歧义。

单参数调用可以省略参数名。多参数调用中，裸标识符与对应参数同名时可以缩写：

```norm
Integer difference = subtract(left, right)
```

其他多参数实参必须写出名称，具名实参不能与位置实参混用。

实参表达式始终按源码从左到右求值。标签只选择形参槽位，因此 `combine(right: first(), left: second())` 先调用 `first()`，再调用 `second()`。

## 顶层函数

不依赖实例状态的行为直接放在模块顶层：

```norm
Double average(Double left, Double right) {
    return (left + right) / 2.0
}
```

Norm 没有 `static`，因此不需要创建 `MathUtils` 一类只充当函数容器的 class。

## 方法

方法是声明在 class 中、能够访问实例状态的函数。

```norm
class Accumulator {
    Integer total

    add(Integer amount) {
        total = total + amount
    }

    Integer current() {
        return total
    }
}
```

方法调用使用点号：

```norm
Accumulator sum = Accumulator(total: 0)
sum.add(4).add(6)
```

class 方法省略返回类型时是 fluent 方法：它的静态返回类型是包含泛型实参的自身类型，正常到达末尾或执行裸 `return` 都返回 `this`。需要真正无返回值的方法时显式写 `Void`；需要返回其他对象时显式写返回类型。

## 函数类型

函数可以作为值传递。完整函数类型写作 `Function<返回类型(参数类型...)>`：

```norm
Function<Integer(Integer)> operation = square

Integer apply(Integer operation(Integer value), Integer input) {
    return operation(input)
}

Integer doubled = apply(operation: square, input: 2)
```

参数位置上的 callable 声明是 `Function<Integer(Integer)> operation` 的简写。`Function` 不允许省略签名实参。

## 匿名函数

Lambda 可以由上下文推导参数和返回类型，也可以显式声明参数类型：

```norm
Integer incremented = apply(
    operation: (value) { value + 1 },
    input: 4
)

var doubled = (Integer value) { value * 2 }
```

Lambda 的末尾表达式可以直接作为结果；包含其他控制流时使用 `return`。Lambda 可以捕获外层局部、参数和 `this`，被捕获的局部与参数必须满足 effectively-final。

函数引用与绑定方法引用使用下面的形式：

```norm
Function<Integer(Integer)> first = square
Function<Integer(Integer)> second = counter::add
Integer add(Integer amount) = counter::add
```

## 重载与覆盖

参数名称或类型至少有一项不同时，可以形成重载。任何导致调用点无法唯一确定目标函数的声明都非法。

public 实例方法默认可以被子类覆盖；private 方法不参与覆盖。Norm 不增加 `final`、`open`、`virtual` 或 `override` 关键字。

下一章：[控制流](/language/control-flow)。
