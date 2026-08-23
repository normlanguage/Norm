# 类型系统规范

Norm 使用静态、名义、非空默认的类型系统。编译器在执行前解析每个表达式的类型，并拒绝依赖不安全隐式转换的程序。

## 类型类别

- 基本类型：Boolean、整数、浮点、Decimal、String；
- 用户类型：class、value、interface、enum、annotation；
- 参数化类型：`List<T>`、`Map<K, V>` 等；
- nullable 类型：`T?`；
- 函数类型：`R function(P...)`；
- 位置引用类型：`ref<Value>`；
- 元类型：`Class<T>` 或规范最终确定的类型描述形式。

Norm 没有统一 Object 根类型。通用行为通过 interface 与泛型约束表达。

## 名义关系

```norm
class Circle extends Shape implements Drawable
```

只有声明的 extends 和 implements 建立子类型。拥有相同字段或方法不会自动兼容。class 单继承，interface 可以多继承。

## Nullability

`String` 不包含 null，`String?` 才包含。nullable 值赋给非空位置前必须通过检查收窄：

```norm
String? input = readInput()
if input != null {
    printLine(input)
}
```

收窄只在变量没有被可能改变的别名或调用写入时保持。`ref<T>` 的可空性尚未定稿。

## 可赋值关系

S 可赋给 T 的主要情况：S 与 T 相同；S 是 T 的声明子类型；安全数值提升；S 是 T? 的非空部分；泛型 use-site variance 允许。收窄、nullable 去除和不同泛型实参之间默认都需要显式处理。

## Value 与 Identity

基本类型、enum 和内建容器按 value 规则赋值；class 赋值保留对象身份。完整规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。`ref<T>` 只引用 value 的存储位置，不接受 class。

## 泛型

泛型默认不变，无 raw type，运行时保留实际类型参数。`? extends T` 和 `? super T` 表达使用位置型变。类型推断必须得到唯一解，否则要求显式实参。

## 确定赋值

局部变量和非空字段在读取前必须已初始化。构造器的每条正常完成路径都要初始化全部必填字段；Norm 不提供绕过规则的 late 初始化关键字。
