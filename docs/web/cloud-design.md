# 云环境设计

云支持通过可替换 adapter 提供，不把某一家云厂商的资源名称写进语言或核心 Web API。

## 能力接口

应用依赖稳定能力，例如 ObjectStore、SecretStore、Queue、KeyValueStore 和 IdentityProvider：

```norm
interface ObjectStore {
    Result<Bytes, StorageError> get(String bucket, String key)
    Result<void, StorageError> put(String bucket, String key, Bytes value)
}
```

厂商 adapter 负责认证、endpoint、重试和错误映射。无法无损映射的特性保留在厂商扩展模块，而不是塞入通用接口的可选参数。

## 身份与秘密

生产环境优先使用工作负载身份和短期凭据。静态 access key 不写入源码、镜像或普通配置文件。SecretStore 返回 `Secret<T>`，日志层默认遮蔽。

## 弹性

应用假设实例可随时被替换：本地磁盘不是持久存储，进程内 session 不能作为唯一状态，启动和关闭都必须有界。区域故障策略由部署架构明确，不由客户端库假装自动解决。

## 可移植性

可移植不等于最低共同能力。核心业务依赖通用接口，高价值的厂商特性可以显式使用；边界通过模块和构造注入保持可见并可测试。

