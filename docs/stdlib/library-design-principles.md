# 标准库 API 原则

标准库把语言的类型和值模型贯彻到系统边界，而不是在库层重新引入隐藏共享、隐式 null 或自动错误传播。

## 类型

public 签名必须完整写出参数与返回类型。集合没有 raw type，缺失使用 Option，可预期失败使用 Result。String、Path、Uuid、Instant 等概念使用不同名义类型。

## 值与共享

普通 class 和集合赋值保持独立。确实需要共享可变对象时，API 使用 Ref 或文档化为共享资源的专门类型。函数不能在没有类型提示的情况下把传入值保存为全局共享状态。

## 错误

- Result：解析失败、文件不存在、连接拒绝等调用者可能处理的结果；
- Option：查找不存在且不需要错误细节；
- Exception：不变量破坏或无法作为正常契约继续的执行失败。

API 不用 null、负数或空字符串作为多义错误哨兵。

## 资源

内存由运行时管理，文件、socket、进程和数据库连接确定性关闭。作用域 API 必须在正常返回、Result 失败和 Exception 路径都执行清理。

## 环境依赖

时区、locale、encoding、舍入、超时、随机源和安全策略通过参数或显式配置提供。测试可以替换 Clock、Random、文件系统或网络 adapter。

## 兼容性

参数名属于命名调用契约。新增 overload 不能造成旧调用歧义；enum 新 variant、错误 code 和序列化 schema 都按 public API 管理。

