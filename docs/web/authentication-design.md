# 身份认证设计

认证属于 Web 库，不是 Norm 语言语法。应用通过显式组件读取凭据、验证身份并把认证结果放入请求上下文，不使用方法 annotation 隐式拦截。

## 核心类型

```norm
enum AuthenticationResult {
    Anonymous
    Authenticated(Principal principal)
    Rejected(AuthenticationError error)
}

interface Authenticator {
    AuthenticationResult authenticate(HttpRequest request)
}
```

`Anonymous` 表示请求没有提供凭据，`Rejected` 表示提供了无效凭据。二者必须区分，才能正确生成 401、匿名访问或审计事件。

## 会话与 Token

会话认证使用随机、不可预测的 session id，并在服务端保存可撤销状态。Token 认证必须显式配置 issuer、audience、时钟偏差和允许的签名算法；不能根据 token header 自动接受算法。

```norm
TokenAuthenticator auth = TokenAuthenticator(
    issuer = expectedIssuer,
    audience = "orders-api",
    keys = keyProvider
)
```

## 请求管线

认证 middleware 只建立 Principal。授权由路由处理函数或独立 Authorizer 根据资源和动作判断。密码、token 和 cookie 不写入日志；认证失败对客户端返回稳定错误，对内部审计保留原因。

