# 函数声明语法

```text
Function := Visibility? ReturnType Identifier TypeParameters?
            "(" Parameters? ")" Block
Parameter := Type Identifier ("=" ConstantExpression)?
```

```norm
public Integer clamp(Integer value, Integer minimum, Integer maximum) {
    if value < minimum { return minimum }
    if value > maximum { return maximum }
    return value
}
```

## 参数

参数在函数体内是局部绑定。多参数调用使用 `name: value`，参数名因此属于 public API。单参数调用可以省略名称；多参数调用中的裸标识符只有与对应参数同名时才能省略标签。默认值必须是编译期常量，并且默认参数位于必填参数之后。

参数标签决定结果绑定到哪个形参，但所有实参表达式始终按源码从左到右求值。未知、重复或缺失标签属于编译错误，`name = value` 不是调用语法。

```norm
Integer result = clamp(value: 120, minimum: 0, maximum: 100)
```

## 返回

`Void` 函数可以正常到达末尾；其他返回类型的每条正常完成路径必须执行 `return value`。Norm 不使用最后表达式隐式返回。

函数可以声明在模块顶层或类型内部。顶层函数不需要 class 容器，也不存在 `static` 修饰符。重载和函数值见[高级函数规则](/spec/grammar/functions-advanced)。

