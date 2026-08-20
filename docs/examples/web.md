# Web 应用

```norm
@Entity(table = "users")
class User {
    long id
    String name
    String? email
}

value CreateUserRequest {
    String name
    String? email
}

@Post(path = "/users")
HttpResponse<User> createUser(@Body CreateUserRequest request, Ref<UserService> service) {
    Result<User, UserError> result = service.create(request = request)

    return switch result {
        case Ok(User user) {
            break HttpResponse.ok(body = user)
        }
        case Err(UserError.InvalidName) {
            break HttpResponse.badRequest(message = "Invalid user name")
        }
    }
}
```

Norm Web 的方向是 Java/Spring 的工程性与 Go 的直接性之间：annotation 显式声明框架行为，Result 表达业务失败，Ref 明确共享依赖，reified generics 支持强类型 JSON/HTTP。
