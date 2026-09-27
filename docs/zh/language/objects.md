# Class、Value 与身份

Class 实例具有身份。基本类型、enum 和内建容器是值。规范性规则见[Value 与 Identity 语义](/zh/spec/value-identity-semantics)。

## Class 保留身份

```norm
class Counter {
    Integer value

    Void increment() {
        value = value + 1
    }
}

Counter first = Counter(value: 0)
Counter second = first
second.increment()
printLine(first.value)
```

程序打印 `1`：两个变量指向同一个 Counter。参数与返回值遵守相同规则。

需要新的顶层身份时调用 `copy()`。Value 字段会独立，而 class 字段仍指向原先嵌套的对象。

## 构造与继承

Class 可以声明一个同名构造器，不带返回类型或可见性修饰符。子类通过 `extends` 单继承，并在构造器中首先调用 `super(...)`。Public 方法按签名覆盖并动态分派。规范性规则见[Class 声明](/zh/spec/grammar/classes)。

## 容器是值

复制内建容器会创建独立结构。其中的 class 元素仍保留身份。Value 按结构比较，class 按身份比较。

## 用户定义的 Value

```norm
value Point {
  Integer x
  Integer y
}
```

用户定义的 value 可以包含方法和泛型参数，也可以实现接口。其字段在构造后不可赋值。赋值、传参、返回和字段读取保持逻辑独立；相等与 hash 递归使用各字段的语言语义。`value` 只在顶层声明头中是上下文关键字，在其他位置仍可作为普通标识符。

## `ref<T>` 标识值的存储位置

`ref<T>` 引用 value 存储位置，不用于共享 class 实例。`&location` 取地址，`*reference` 读写位置。复制 ref 保留位置身份；ref 受局部变量和调用的词法边界约束。完整规则见 [`ref<T>` 语法](/zh/spec/grammar/references)。
