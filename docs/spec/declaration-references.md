# 声明引用与反射

Norm 把类型、字段和 callable 的引用绑定到 Core 声明 identity。重命名会由工具链更新引用；目标被删除、不可见或重载无法唯一确定时，编译失败。运行时 API 不接受字符串声明名或公开 ordinal。

## 引用语法

| 目标 | 语法 | 类型 |
| --- | --- | --- |
| 类型 | `User.class` | `Class<User>` |
| nullable 或泛型类型 | `String?.class`、`List<String>.class` | `Class<String?>`、`Class<List<String>>` |
| 字段 | `User.id.field` | `Field<User, UserId>` |
| 顶层函数 | `findUser.function` | `Function<User(UserId)>` |
| 未绑定方法 | `UserService.findUser.function` | `Function<User(UserService, UserId)>` |
| 绑定方法 | `service.findUser` | `Function<User(UserId)>` |

`.function` 指向声明；普通成员访问则产生可调用的绑定函数值。未绑定方法把名为 `this` 的 owner 参数放在签名首位，调用仍执行动态分派。

## 类型化 metadata

| 类型 | 稳定能力 |
| --- | --- |
| `Class<T>` | `name()`、`annotation<A>()`、`fields()`、`functions()`、`constructors()` |
| `Field<Owner, Value>` | `name()`、`type()`、`owner()`、`annotation<A>()`、`read(Owner)` |
| `Function<Signature>` | `name()`、`owner()`、`parameters()` |
| `Parameter<Value>` | `name()`、`type()`、`function()` |
| `Constructor<T>` | `owner()` |

`Field<Owner, Value>.type()` 返回 `Class<Value>`，`owner()` 返回描述符中的 `Class<Owner>`。通过 `Class<T>.fields()` 枚举继承字段时，`Owner` 是当前的 `T` 视图；直接写 `Base.value.field` 时则是 `Base`。`read(receiver: ...)` 要求一个 `Owner` 实例，并以字段的精确 `Value` 类型返回值。

`Class<T>.fields()` 返回 `List<Field<T, ?>>`，`functions()` 返回 `List<Function<?>>`，`constructors()` 返回 `List<Constructor<T>>`。每个重载都是独立元素；异构集合使用 `?` 隐藏不同的字段值类型或函数签名。

## 重载

将重载声明引用赋给精确函数类型时，编译器使用期望签名选出唯一声明：

```norm
Function<User(UserService, UserId)> lookup = UserService.findUser.function
```

`Class<T>.functions()` 和 `constructors()` 则保留所有重载，供 metadata 查询。`Function<?>` 不知道可调用签名，因此不能直接执行。

## 实现真相源

反射成员和精确类型由 [`BuiltinCatalog`](https://github.com/w0fv1/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/builtin/BuiltinCatalog.java) 定义，二进制 metadata 中的声明引用由 [`CoreAnnotationReference`](https://github.com/w0fv1/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreAnnotationReference.java) 表示。
