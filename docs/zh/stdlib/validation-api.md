# Validation API

`std.validation` 提供作用于函数参数和对象字段的强类型 Annotation 约束。参数在进入函数体前校验；字段在初始化、赋值和 `ref<T>` 写入前校验。失败抛出 `ConstraintViolation`，对应写入不会提交。

```norm
import std.validation.CodePointSize
import std.validation.Min
import std.validation.NotBlank

class Account {
  @NotBlank()
  String owner

  @Min(value: 0)
  Integer balance
}

Void register(@CodePointSize(minimum: 3, maximum: 32) String name) {
}
```

## 公共契约

所有内置约束实现 `Constraint<T>`。它组合 `ParameterInterceptor<T>`、`FieldInterceptor<T>` 与 `SourceRetention`，并以 `isValid(T)`、稳定 `code()` 和展示 `message()` 定义约束。用户 Annotation 实现同一 interface 即可复用相同生命周期和失败模型。

`ConstraintViolation` 提供：

- `location`：`ConstraintLocation.Parameter` 或 `ConstraintLocation.Field`；
- `functionReference`：参数约束所属的 `Function<?>`；
- `parameterReference`：参数约束的 `Parameter<?>`；
- `fieldReference`：字段约束的 `Field<?, ?>`；
- `code`：稳定机器标识；
- `message`：从 `Exception` 继承的默认展示文本。

`location` 决定哪组声明引用非空：Parameter 位置提供 function 和 parameter，Field 位置提供 field。名称和类型通过引用查询，不在异常中再存一份副本。

实现与完整声明以 [`validation/constraints.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/validation/constraints.norm) 为准。

## 内置约束

| 类型 | Annotation |
| --- | --- |
| `Boolean` | `AssertTrue`、`AssertFalse` |
| `Integer` | `Min`、`Max`、`Negative`、`NegativeOrZero`、`Positive`、`PositiveOrZero` |
| `String` | `NotEmpty`、`NotBlank`、`CodePointSize`、`GraphemeSize` |

文本范围分别由 `CodePointSize` 和 `GraphemeSize` 表达 Unicode code point 与 grapheme cluster 语义。空值约束由默认非空的 `T` 和显式可空的 `T?` 类型表达。

`CodePointSize` 与 `GraphemeSize` 要求 `0 <= minimum <= maximum`；无效定义在首次执行 Annotation 时抛出 `ConstraintDefinitionException`。
该异常通过 `code` 提供对应约束的稳定机器标识。

## 自定义约束

```norm
import std.validation.Constraint

annotation Even implements Constraint<Integer> {
  public Boolean isValid(Integer value) {
    return value % 2 == 0
  }

  public String code() {
    return "even"
  }

  public String message() {
    return "must be even"
  }
}
```

多个约束遵循 Annotation 源码顺序，遇到首个失败即抛出。需要收集多项输入错误的解析层应先形成自己的结构化结果，再构造领域对象。
