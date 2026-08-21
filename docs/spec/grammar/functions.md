# 函数声明语法

```text
Function := Visibility? ReturnType Identifier TypeParameters?
            "(" Parameters? ")" Block
Parameter := Type Identifier ("=" ConstantExpression)?
```

```norm
public int clamp(int value, int minimum, int maximum) {
    if value < minimum { return minimum }
    if value > maximum { return maximum }
    return value
}
```

## 参数

参数在函数体内是局部绑定。多个参数默认使用命名调用，参数名因此属于 public API。默认值必须是编译期常量，并且默认参数位于必填参数之后。

```norm
int result = clamp(value = 120, minimum = 0, maximum = 100)
```

## 返回

`void` 函数可以正常到达末尾；其他返回类型的每条正常完成路径必须执行 `return value`。Norm 不使用最后表达式隐式返回。

函数可以声明在模块顶层或类型内部。顶层函数不需要 class 容器，也不存在 `static` 修饰符。重载和函数值见[高级函数规则](/spec/grammar/functions-advanced)。

