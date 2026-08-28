# 序列化运行时计划

## 目标

建立一套格式无关的结构序列化运行时。类型通过 runtime Annotation 显式加入序列化契约，字段结构来自 Core metadata；JSON、XML 与 YAML 复用同一份结构描述、字段访问和构造路径。

```norm
@Serializable()
value User {
  @SerialName(name: "user_name")
  String name
}

String body = user.toJson()
User decoded = body.fromJson<User>()
```

## 固定决议

- 不引入宏、编译期派生 DSL、运行时代码注入或 classpath 扫描。
- Extension function 是普通顶层函数的静态调用语法；真实方法优先，候选按显式 import 与普通重载规则解析，歧义在编译期报错。
- `@Serializable` 是显式契约标记，不生成方法。`@SerialName` 与 `@SerialIgnore` 只提供 metadata。
- Core aggregate、field ordinal、reified `CoreType` 与 runtime Annotation 是结构信息的单一真相源，不使用 JVM reflection，也不按字符串调用字段或方法。
- 首版只自动处理 `value`。class identity、对象图、循环引用和多态需要独立协议后再进入自动序列化。
- 系统与数据格式失败抛出带路径和位置的类型化异常，不使用 `Result`。

## 语言与 Annotation 基础

Extension function 使用下面的声明形式，首参数是接收者：

```norm
public extension String toJson<T>(T value) {
  return encodeJson(value: value)
}
```

`value.toJson()` 在绑定后等价于 `toJson(value: value)`。Extension 不进入 owner 类型，不改变动态分派表，也不允许隐式导入。

Annotation 的“可应用目标”与“调用拦截生命周期”分离：`TypeTarget`、`FieldTarget`、`FunctionTarget` 和 `ParameterTarget` 只描述目标；`FunctionInterceptor`、`FieldInterceptor<T>` 与 `ParameterInterceptor<T>` 才携带生命周期。序列化 metadata 与 validation 拦截器因此共享目标模型，但不共享行为机制。

## 运行时结构

`Type<T>.fields()` 公开稳定字段名、ordinal、字段类型、runtime Annotation 和受控的 `ReflectedValue` 读取能力。公共反射 API 与序列化运行时都以 Core aggregate 和 field metadata 为结构真相源；序列化热路径直接按 ordinal 访问 `ObjectValue.fields`。

每个精确 reified type 只构建一次 serialization shape：

- aggregate identity 与构造计划；
- 字段 ordinal、字段类型、序列化名称、忽略策略和 nullable 信息；
- 编码字段顺序与解码名称索引。

结构运行时按 exact `CoreType` 缓存 shape，递归 shape 通过延迟引用闭合。`MapperEngine` 按 `(format, exact type)` 分别缓存不可变 reader/writer plan；计划查找之外不持有全局锁。格式 adapter 直接遍历 plan，解码通过规范构造器创建目标 value。只有显式调用 JSON tree API 时才创建 `JsonValue`。

公共入口是 `DataMapper`、`DataReader<T>` 与 `DataWriter<T>`。格式实现只负责 token 与格式 metadata，不复制类型发现、字段访问或对象构造。

## `std.json`

首版提供：

- `JsonValue`：object、array、string、number、boolean 与 null；
- `parseJson`、`writeJson`、`toJsonValue`、`toJson`、`toJsonBytes` 与 `fromJson<T>`；
- `JsonException`：稳定 code、value path、byte offset、line 与 column；
- 最大 nesting、输入字节数、字符串长度与集合元素数限制。

首版内建 shape 包括 String、Boolean、Integer、Long、nullable、Array/List、`Map<String, T>`、enum 和嵌套的 `@Serializable value`。非字符串 map key、非有限浮点数、循环引用和未标记 aggregate 明确拒绝。

JSON token 化与输出使用 Jackson Core streaming API；第三方实现被封装在 format adapter 内，不参与 Norm 类型发现或对象构造。

## `std.yaml`

YAML 与 JSON 复用 Jackson token 映射内核，但拥有独立的 factory、格式策略和异常 ABI。YAML 只接受单文档、字符串 mapping key 与无对象图语义的数据；alias、显式 tag、未知或重复字段明确拒绝。输出采用无 document marker 的稳定 block 形式。

## `std.xml`

XML 使用相同的 mapper 与 shape，通过 Woodstox StAX 流式读写。公共结构 metadata 与 JSON 共用，`@XmlAttribute` 只描述 XML 属性映射。格式严格处理根元素、属性、字段、集合、Map、nullable、DTD 与资源限制；不建立通用 XML tree。

## HTTP 集成

`std.http` 的核心请求 body 继续使用 `Bytes`，不依赖 JSON。同包的泛型 `postJson`/`jsonBody` 组合编码 UTF-8 bytes 并统一设置 `application/json`；响应仍由调用方显式限定大小并解码。

## 执行计划

| 阶段 | 状态 | 可交付结果 |
| --- | --- | --- |
| Extension function | 已完成 | 语法、绑定、重载、Core、格式化与 LSP 统一支持 |
| Annotation 策略分层 | 已完成 | passive target 与 interceptor 生命周期解耦 |
| 结构反射 | 已完成 | field metadata、ordinal 读取、runtime Annotation 查询 |
| 序列化核心 | 已完成 | mapper contract、递归 exact-type shape、ordinal 访问、规范构造器与双向 plan cache |
| `std.json` | 已完成 | Jackson streaming、tree、类型异常与资源限制 |
| `std.xml` | 已完成 | Woodstox streaming、attribute、结构 round-trip、类型异常与资源限制 |
| `std.yaml` | 已完成 | Jackson YAML streaming、共享 token mapper、严格单文档与类型异常 |
| HTTP JSON | 已完成 | `Bytes` 边界上的 JSON 请求组合与真实回环集成测试 |
| 性能与收口 | 已完成 | exact-type 缓存门禁、JVM/Native Image 验证与架构审查 |

每阶段先写 parser/Core/runtime 或真实集成测试，再实现；本表是执行状态的唯一入口。

## 验收

- `@Serializable value` 能递归 round-trip，重命名、忽略、nullable、集合、enum 与 Unicode 行为确定。
- 未支持类型、重复序列化名、未知字段、缺字段、数值溢出、深度与大小超限产生精确异常。
- 编码字段访问与对象构造只走 ordinal；解码只使用预计算名称索引，不按字符串调用方法，不使用 JVM reflection 或中间 JSON tree。
- 同一精确类型的 shape plan 在 program 内复用，缓存不跨错误的 module/type identity。
- 回环 HTTP 测试覆盖 JSON request bytes、header 与 response decode；公网 smoke 只位于 `norm/tests/live`。
- JVM 与 Native Image 的公开行为一致。
