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
| `Class<T>` | `name()`、`isValue()`、`annotation<A>()`、`fields()`、`functions()`、`constructors()` |
| `Field<Owner, Value>` | `name()`、`type()`、`owner()`、`annotation<A>()`、`isPublic()`、`hasAnnotation(Class<A>)`、`identity(Owner)`、`read(Owner)`、`bind(Owner)`、`write(receiver: Owner, value: Value)`、`copy(target: Owner, source: Owner)` |
| `Function<Signature>` | `name()`、`owner()`、`parameters()` |
| `Parameter<Value>` | `name()`、`type()`、`function()` |
| `Constructor<T>` | `owner()` |

`Field<Owner, Value>.type()` 返回 `Class<Value>`，`owner()` 返回描述符中的 `Class<Owner>`。通过 `Class<T>.fields()` 枚举继承字段时，`Owner` 是当前的 `T` 视图；直接写 `Base.value.field` 时则是 `Base`。`read(receiver: ...)` 要求一个 `Owner` 实例，并以字段的精确 `Value` 类型返回值。

`Class<T>.fields()` 返回 `List<Field<T, ?>>`，`functions()` 返回 `List<Function<?>>`，`constructors()` 返回 `List<Constructor<T>>`。每个重载都是独立元素；异构集合使用 `?` 隐藏不同的字段值类型或函数签名。

计算属性不产生存储字段，因此不增加 `fields()` 的条目。访问器进入 `functions()`，getter 和 setter 保留同一属性名及各自的 callable identity，参数列表包含接收者；验证见 `PropertyExecutionTest.reflectsPropertyAccessorsWithoutInventingStorageFields`。

## 运行时对象与字段复制

`classOf(value)` 返回对象实际类型的 `Class<T>`，其中 `T` 保留实参的静态类型。通过接口或父类持有对象时，`fields()` 仍枚举实际类型的字段。`Class<?>` 描述符的相等性和哈希只取决于所表示的类型，不取决于调用处的静态视图。`isValue()` 表示该类型是否具有值语义。

`Field.bind(receiver)` 产生 `FieldHandle<Value>`，持有可变 class 实例和字段位置。句柄可返回、存入对象并被闭包捕获；`read()` 读取当前值，`write(value)` 使用正常字段写入路径。同一对象的同一字段句柄相等，不同对象的字段句柄不相等。它不捕获局部变量位置，也不改变 `ref<T>` 的词法生命周期规则。 当期望类型为 `FieldHandle<T>` 时，可直接传入可访问的 class 存储字段；例如 `binding(model.title)` 自动捕获字段，接收者只求值一次。已有句柄保持原样传递，泛型调用可从字段类型推导 `T`。局部变量、计算属性、value 字段和 null-safe 字段访问不进行这种转换。

`Field.write(receiver: ..., value: ...)` 按字段的精确值类型写入可变 class，复用正常字段写入、注解拦截和变化通知；运行时校验接收者和实际字段类型。异构 `Field<T, ?>` 不能写入任意值。

导入 `std.observation.onChange` 后，可使用 `model.title.onChange { ... }` 订阅字段赋值。该 extension 的接收者是 `FieldHandle<T>`，复用字段捕获；回调参数为 `oldValue`、`newValue`，保留字段的 nullable 类型。订阅不立即执行，按字段值相等规则过滤重复赋值；返回 `Resource`，`close()` 可重复调用并停止订阅。

订阅自动接入创建时的[资源归属上下文](execution-context.md#资源归属)。GUI 组件提供该上下文，因此在 `init()` 或组件回调内注册的订阅会随组件销毁释放，变化回调也恢复原组件上下文；提前 `close()` 会立即解除归属。

通知携带该次变化的旧值和新值快照，回调内再次修改不会覆盖外层通知的参数。订阅回调抛出普通异常时，其他有效订阅仍收到本次变化，之后向修改调用方传播异常。

集合字段的整体替换、原地修改及嵌套值容器修改均可被观察；相等替换和没有改变内容的操作不通知。替换字段或嵌套容器后，订阅跟随当前字段中的值；复制出的集合不继承订阅。class 元素内部字段的修改不属于容器结构变化，应订阅该对象的字段。实现与执行覆盖见 [FieldHandleExecutionTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/truffle/FieldHandleExecutionTest.java)。

`Field.isPublic()` 反映字段声明的可见性。`Field.copy(target: ..., source: ...)` 在相同字段描述符约束下复制字段，执行正常字段写入、注解拦截和变化通知；运行时检查两端对象的类型及泛型实参。目标必须是可变 class，不能通过反射修改 value。异构字段集合可以直接调用 `copy`，无需将字段值降为 `Any`。

运行验证见 [ObjectReflectionExecutionTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ObjectReflectionExecutionTest.java)。

`Field.hasAnnotation(Class<A>)` 查询运行时保留的注解是否属于指定注解类型或实现指定接口，不实例化注解。`std.annotation.IdentityField` 是身份字段的通用标记。

`Field.identity(receiver)` 返回不透明值类型 `FieldIdentity`，将 owner 类型、字段声明和字段值快照作为相等性及哈希依据，不经过字符串转换。字段值遵循 Norm 的值/对象身份语义；空值不构成身份。异构反射字段可直接生成身份，无须公开隐藏的字段值类型。验证见 [FieldIdentityExecutionTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/truffle/FieldIdentityExecutionTest.java)。

## 重载

将重载声明引用赋给精确函数类型时，编译器使用期望签名选出唯一声明：

```norm
Function<User(UserService, UserId)> lookup = UserService.findUser.function
```

`Class<T>.functions()` 和 `constructors()` 则保留所有重载，供 metadata 查询。`Function<?>` 不知道可调用签名，因此不能直接执行。

## 实现真相源

反射成员和精确类型由 [`BuiltinCatalog`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/builtin/BuiltinCatalog.java) 定义，二进制 metadata 中的声明引用由 [`CoreAnnotationReference`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreAnnotationReference.java) 表示。
