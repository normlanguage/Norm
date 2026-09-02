# 函数声明语法

```text
Function := Visibility? "extension"? ReturnType? Identifier TypeParameters?
            "(" Parameters? ")" Block
Parameter := Type Identifier ("=" Expression)?
```

```norm
Integer subtract(Integer left, Integer right) {
    return left - right
}
```

## Extension function

`extension` 只修饰顶层函数，返回类型和至少一个参数必须显式声明。首参数是接收者，其余参数保留普通函数的标签规则。

```norm
extension String quoted(String value) {
  return "\"" + value + "\""
}

String text = "Norm".quoted()
```

点号调用在绑定后成为普通函数调用，接收者作为第一个实参先求值。Extension 必须处于当前 package 或被显式 import；实例方法按名称优先，多个同等匹配的 extension 是编译错误。Extension 不进入类型的方法表或动态分派表。

## 参数

参数在函数体内是局部绑定。多参数调用使用 `name: value`，参数名因此属于 public API。单参数调用可以省略名称；多参数调用中的裸标识符只有与对应参数同名时才能省略标签。默认参数必须位于必填参数之后；省略实参时，默认表达式在调用位置按参数顺序求值。

参数标签决定结果绑定到哪个形参，但所有实参表达式始终按源码从左到右求值。未知、重复或缺失标签属于编译错误，`name = value` 不是调用语法。

```norm
Integer result = subtract(left: 120, right: 100)
```

## 返回

普通顶层函数省略返回类型时，声明类型固定为 `Void`。Extension 必须显式声明返回类型。class 方法省略返回类型时，声明类型固定为完整的 owner 类型；正常到达末尾和裸 `return` 产生 `this`，`return value` 非法。显式 `Void` 始终表示无结果。

除此之外，非 `Void` 具名函数的每条正常完成路径必须执行 `return value`。interface 方法必须显式声明返回类型。Lambda 的末尾表达式规则见[高级函数规则](/spec/grammar/functions-advanced)。

函数可以声明在模块顶层或类型内部。顶层函数不需要 class 容器，也不存在 `static` 修饰符。重载和函数值见[高级函数规则](/spec/grammar/functions-advanced)。
