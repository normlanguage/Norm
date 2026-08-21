# Web 安全

安全默认值必须在库中可见且可测试。认证、授权、输入验证和事务安全是不同边界，不能由一个“安全开启”开关代替。

## 认证

Session、token 和 OAuth adapter 都实现 Authenticator。无凭据、无效凭据和基础设施故障使用不同结果。密码、token、cookie 和授权 Header 永不进入普通日志。

## 授权

```norm
Authorization decision = authorizer.check(
    principal: request.principal,
    action: OrderAction.Read,
    resource: order
)
```

授权在读取具体资源后执行时，可以使用租户、所有者和状态。拒绝默认返回最少信息，避免通过 403/404 差异泄露资源存在性。规则通过普通函数或 Authorizer 注册，不依赖 annotation。

## 输入边界

服务器限制 request line、header、body、multipart、JSON 深度和处理时间。解析成功不代表业务有效；Validator 返回结构化错误。文件路径、重定向 URL 和代理 Header 需要专门规范化与 allowlist。

## 浏览器安全

Cookie 默认 Secure、HttpOnly，并明确 SameSite。状态改变请求使用 CSRF 防护；CORS 只允许配置的 origin、method 与 header，不能把带凭据请求和 `*` 组合。

## 响应

平台提供安全 header 基线，包括内容类型嗅探保护和可配置 CSP。内部异常只向客户端返回稳定错误 code 与 trace id，详细 stack 留在受控日志。

依赖版本、密钥轮换、审计事件和漏洞响应属于部署安全的一部分，必须进入发布和运维流程。

