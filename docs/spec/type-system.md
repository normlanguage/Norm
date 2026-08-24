# 类型系统规范

Norm 使用静态、名义、非空默认的类型系统。编译器在执行前解析每个表达式的类型，并拒绝依赖不安全隐式转换的程序。

## 类型类别

- 基本类型：`Boolean`、`Integer`、`Long`、`Float`、`Double`、`Number`、`CodePoint`、`String`；
- 用户类型：class、interface、数据 enum；
- 参数化类型：`List<T>`、`Map<K, V>` 等；
- nullable 类型：`T?`；
- 函数类型：`R function(P...)`；

Norm 没有统一 Object 根类型。通用行为通过 interface 与泛型约束表达。

## 名义关系

只有 class 的 `implements` 与 interface 的 `extends` 建立名义关系。拥有相同字段或方法不会自动兼容。

## Nullability

`String` 不包含 null，`String?` 才包含。nullable 值赋给非空位置前必须通过检查收窄：

```norm
String? input = readInput()
if input != null {
    printLine(input)
}
```

收窄只在变量没有被可能改变的路径写入时保持。

## 可赋值关系

S 可赋给 T 的主要情况：S 与 T 相同；S 显式满足 T 的 interface 关系；S 是 T? 的非空部分；S 是具体数字叶类型且 T 为 `Number`。不同数字叶类型之间不做非字面量隐式转换，参数化类型保持不变。

集合字面量的元素类型使用最精确公共类型。若具体元素类型不同但全部满足 `Stringable`，该接口可以成为公共类型；没有共同类型时仍产生类型错误。

## Value 与 Identity

基本类型、enum 和内建容器按 value 规则赋值；class 赋值保留对象身份。完整规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。

## 泛型

泛型不变，无 raw type，运行时保留实际类型参数。类型推断必须得到唯一解，否则要求显式实参。

## 确定赋值

局部变量和非空字段在读取前必须已初始化。构造器的每条正常完成路径都要初始化全部必填字段；Norm 不提供绕过规则的 late 初始化关键字。
