# `ref<T>` 引用语法

`ref<T>` 用于表达 value 存储位置的身份。它不负责 class 共享：class 实例本身已经具有身份。

## 语法

```text
引用类型     ::= "ref" "<" 类型 ">"
取地址表达式 ::= "&" 可寻址位置
读取表达式   ::= "*" 一元表达式
写入语句     ::= "*" 一元表达式 "=" 表达式
```

`&` 与 `*` 是一元运算符。读取产生 `T` 的普通 value，写入替换目标位置中的 value。复制 `ref<T>` 保留位置身份；两个同类型 ref 使用 `==`、`!=` 比较位置身份。

## 可寻址位置

可寻址位置只包括可写局部变量、参数和 class 的 value 字段。字面量、临时表达式、调用结果、value 字段、容器元素和 null-safe member access 不可取地址。位置一旦被取地址，执行器必须在其词法生命周期内保持稳定 identity。

## 类型边界

- `T` 只能是 value 类型；
- `ref<Class>` 不合法；
- `ref<ref<T>>` 与 nullable ref 不合法；
- ref 只允许作为局部变量类型或 callable 参数类型；
- ref 不能作为返回类型、字段、enum payload、泛型实参或 function type 的组成部分。

## 生命周期

0.10 不引入命名生命周期或生命周期注解。ref 的生命周期由局部声明所在词法作用域或一次 callable 调用界定。ref 可以复制、重新赋值和传给 ref 参数，但不能返回、存入对象或容器，也不能被 lambda 捕获。上述限制使任何 ref 都不能越过其存储位置的有效期。

已确定的 value、class 和容器规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。
