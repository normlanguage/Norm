# Value 与 Identity 语义

本文是 Norm 赋值、传参、返回、相等和复制行为的权威规范。对象模型、容器和未来的引用语法都以这里的规则为准。

## 数据类别

Norm 区分 value 与 identity：

| 类别 | 类型 | 赋值、传参和返回 | `==` |
| --- | --- | --- | --- |
| Value | `Integer`、`Boolean`、`String`、enum、内建容器 | 产生逻辑独立的值 | 结构相等 |
| Identity | `class` 实例 | 复制对象引用，共享同一对象 | 对象身份相等 |

统一的 `=` 复制右侧表达式的值。class 变量保存的值是对象引用，因此复制该值会共享对象；容器保存的是容器值，因此复制后容器结构彼此独立。

```norm
Box first = Box(value: 1)
Box second = first
second.value = 2
printLine(first.value)
```

这里输出 `2`。

## Class 与显式复制

class 实例具有稳定身份。普通赋值、参数传递和函数返回不会隐式创建新对象。

每个 class 都提供 `copy()`：它创建新的顶层对象身份，并逐字段执行普通赋值语义。

```norm
Box second = first.copy()
```

值字段因此逻辑独立；class 字段仍指向原来的嵌套对象。Norm 不提供隐式递归深复制。

## 容器

`Array`、`List`、`Map`、`Set`、`Stack`、`Queue`、`Deque`、`Pair`、`Range` 与 `StringBuilder` 是 value。复制容器会复制其结构，并对每个元素执行普通赋值语义。

因此，容器中的 value 元素逻辑独立，class 元素保留对象身份。容器相等按内容递归比较；作为 Map key 或 Set 元素时也使用相同相等规则。

## 求值和调用

实参表达式严格按源码从左到右求值，参数标签只决定求值结果绑定到哪个形参，不改变求值顺序。

多参数调用必须使用 `name: value`。裸标识符只有与同位置形参同名时才能作为简写；单参数调用可以省略标签。

```norm
merge(left: mergeSort(left), right: mergeSort(right))
```

## 实现自由

逻辑独立不要求立即深复制。执行器可以使用写时复制、结构共享、逃逸分析或复制消除，但不能改变身份、相等、修改结果和源码求值顺序。

## `ref<T>` 方向

`ref<T>` 表示 value 存储位置的身份，而不是 class 共享机制。它只接受 value 类型；`ref<Class>` 不合法，因为 class 已经具有身份。

`ref<T>` 的取地址、解引用和相等语法仍需由语法规范确定。
