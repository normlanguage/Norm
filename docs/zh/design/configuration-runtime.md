# 配置映射运行时

## 目标

框架配置由普通 Norm value 表达，并从一份 Core 结构元数据派生宿主属性。应用声明、IDE 类型信息、结构序列化与 Java 框架启动共享同一类型定义，框架适配器不手写字符串属性表。

## 结构规则

- 根值及嵌套值显式使用 `@Serializable`；
- 未重命名字段从 camelCase 转为 kebab-case；
- `@SerialName` 和 `@SerialIgnore` 与其他结构映射格式共用；
- null 不产生属性；
- List/Array 使用 `[index]`，String Map key 成为路径段；
- enum variant 使用 kebab-case；
- `@ConfigurationKey` 表达命名集合；
- `@ConfigurationValue` 表达标量包装值。

## 运行时边界

`configurationProperties<T>()` 通过 reified `Class<T>` 取得精确 CoreType，复用 Serialization Runtime 缓存的 shape，并按 field ordinal 读取 value。输出是插入有序的宿主 Map，可直接穿过 Java Binding 边界。实现不读取 JVM field、不按字符串调用 getter，也不建立 JSON/YAML 中间树。

框架模块只定义对应框架配置层级的 Norm 类型和少量语义构造函数。启动器只负责生命周期和把映射结果传入官方框架入口。
