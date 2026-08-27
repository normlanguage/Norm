# 标准库 API 原则

标准库把语言的类型和值模型贯彻到系统边界，而不是在库层重新引入隐藏共享、隐式 null 或自动错误传播。

## 类型

public 签名必须完整写出参数与返回类型。集合没有 raw type，普通缺失使用 nullable，应用领域中确实属于返回值组成部分的互斥结果可以使用 Result。String、Path、Uuid、Instant 等概念使用不同名义类型。

通用协议统一使用 `std.core` 的 interface，并由类型显式声明 `implements`。库不引入平行的 trait、typeclass 或结构化匹配机制，也不通过 Comparable、Equatable 等接口改变语言操作符语义。

## 值与共享

class 参数保留对象 identity，集合等 value 参数产生逻辑独立值。需要共享 value 存储位置时使用 `ref<T>`；连接池等资源使用具有明确生命周期的 class。函数不能在契约未说明时把传入对象长期保存。

## 错误

- nullable：查找不存在且不需要错误细节；
- Result：应用领域中由函数契约显式返回的互斥结果；
- Exception：系统操作失败、参数或状态错误，以及无法作为正常返回值继续的失败。

API 不用 null、负数或空字符串作为多义错误哨兵。

## 系统层

当前系统层开发范围包括 std.io、filesystem、network、http、time、process、regex、crypto 与 concurrent。系统层 public API 不使用 Result 表达失败；文件不存在、权限不足、连接拒绝、超时、无效 pattern、密码学操作失败和并发状态错误等失败抛出对应领域的 Norm Exception。宿主异常只在 adapter 边界出现，并统一转换为可捕获的 Norm Exception。

运行时分层与宿主接入见[系统运行时架构](/design/system-runtime)。

## 资源

内存由运行时管理，文件、socket、进程和数据库连接确定性关闭。作用域 API 必须在正常返回和 Exception 路径都执行清理。

## 环境依赖

时区、locale、encoding、舍入、超时、随机源和安全策略通过参数或显式配置提供。测试可以替换 Clock、Random、文件系统或网络 adapter。

## 兼容性

参数名属于命名调用契约。新增 overload 不能造成旧调用歧义；enum 新 variant、错误 code 和序列化 schema 都按 public API 管理。
