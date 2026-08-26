# Class、Value 与 Identity

Norm 的 class 实例具有身份；基本类型、enum 和内建容器是 value。完整规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。

## Class 共享身份

```norm
class Counter {
    Integer value

    increment() {
        value = value + 1
    }
}

Counter first = Counter(value: 0)
Counter second = first
second.increment()
printLine(first.value)
```

程序输出 `1`。`first` 与 `second` 指向同一个 Counter，函数参数和返回值也遵循相同规则。省略返回类型的 class 方法返回同一接收者，因此可以继续链式调用。

需要新身份时显式调用 `copy()`：

```norm
Counter second = first.copy()
second.increment()
```

`copy()` 只创建新的顶层对象。若字段也是 class，原对象和副本仍共享该字段指向的对象。

## 构造与继承

class 可以声明一个同名、无返回类型和可见性修饰符的构造器。子 class 使用 `extends` 单继承，并在构造器第一项显式调用 `super(...)`；public 方法按签名覆盖并动态分派。完整规则见 [Class 声明](/spec/grammar/classes)。

## 容器是 Value

```norm
List first = List()
first.add(1)

List second = first
second.add(2)
```

`first.size()` 是 `1`，`second.size()` 是 `2`。容器结构被复制；如果元素是 class，元素的对象身份仍然共享。

value 使用结构相等，class 使用身份相等：

```norm
printLine(first == second)
printLine(counter == counter.copy())
```

## 用户定义 Value

```norm
value Point {
  Integer x
  Integer y
}
```

用户定义 value 可以声明方法、泛型参数并实现 interface。字段在构造后不可赋值；赋值、传参和返回产生逻辑独立值，相等与 hash 递归使用字段的语言内建语义。`value` 只在顶层声明头中作为上下文关键字，普通标识符仍可使用这个名字。

## `ref<T>`：Value 存储位置

`ref<T>` 用于引用 value 的存储位置，不用于 class 共享。复制 ref 后仍指向同一位置；具体取地址、读写和生命周期边界见 [`ref<T>` 引用语法](/spec/grammar/references)。

下一章：[函数](/language/functions)。
