# Session

session 使用客户端 cookie 中的随机 id 关联服务端状态。默认设计不把完整可变对象序列化进 cookie。

```norm
SessionConfig config = SessionConfig(
    cookieName = "session",
    idleTimeout = Duration.minutes(value = 30),
    absoluteTimeout = Duration.hours(value = 12),
    secure = true,
    httpOnly = true,
    sameSite = SameSite.Lax
)
```

## 生命周期

首次需要写 session 时创建 id；登录和权限提升后必须 rotate，退出时删除服务端记录并过期 cookie。idle timeout 与 absolute timeout 独立检查。

## 存储

SessionStore 提供 load、save、rotate 和 delete。多实例部署使用共享存储或一致路由；进程内 Map 只适合开发。并发请求更新同一 session 时需要版本检查，避免后写覆盖先写。

## 数据边界

session 只保存小型、稳定、可撤销的身份或流程数据。大型缓存、数据库实体和长期业务状态不放入 session。敏感字段在存储层加密，日志只记录不可逆 session 标识摘要。

CSRF 防护与 session 认证配套配置，SameSite 不能作为唯一防护。

