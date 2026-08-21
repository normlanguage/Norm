# 声明语法

声明创建模块成员、类型成员或局部绑定。Norm 使用类型前置，让 API 形状在名称之前可见。

## 顶层类别

```text
Declaration := Visibility? (
    ClassDeclaration
  | ValueDeclaration
  | InterfaceDeclaration
  | EnumDeclaration
  | AnnotationDeclaration
  | FunctionDeclaration
)
```

## Class 与 Value

```norm
class Counter {
    int value
    void increment() { value = value + 1 }
}

value Point {
    int x
    int y
}
```

class 可以包含可变字段和行为，并且赋值保留对象 identity；value 构造后不可变并按 value 规则赋值。二者字段都必须满足确定赋值。

## Interface

```norm
interface Formatter<T> {
    String format(T value)
}
```

interface 只声明行为，满足关系必须显式写 implements。

## Enum

```norm
enum State {
    Active,
    Disabled(String reason)
}
```

variant 参数是其携带数据的完整声明。enum 封闭且可由 switch 穷尽。

## 函数

```norm
String format(Point point) {
    return "(${point.x}, ${point.y})"
}
```

返回类型、参数类型和 public 参数名都是签名的一部分。只改变返回类型不能构成 overload。

## Annotation

Annotation 声明编译期元数据的字段和默认值，不执行构造代码，也不能改变普通语句语义。

## 重复与作用域

同一作用域中不能声明冲突名称。局部变量从声明后到块末尾可见；类型参数只在所属声明及其成员签名/实现内可见。
