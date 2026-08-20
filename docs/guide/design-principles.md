# Norm 设计白皮书：为什么这样设计

Norm 的目标不是创造最多语言特性的语言，而是创造一门适合长期维护业务软件的语言。

现代应用开发存在几个长期问题：

- 类型系统越来越复杂，学习成本不断提高。
- 元编程能力越来越强，但代码行为越来越难预测。
- 对象模型经常混淆值、引用和共享状态。
- Web 应用大量依赖框架魔法，业务逻辑被隐藏。
- 部署体验和开发体验之间存在矛盾。

Norm 的设计原则：

## 显式优于隐式

Norm 希望开发者看到代码时，可以大致理解真实行为。

因此：

- 没有宏。
- 没有隐式类型转换。
- 没有隐式 nullable。
- 没有隐藏共享状态。
- 元编程必须通过 annotation 和 reflect 明确表达。

## 面向应用开发

Norm 优先服务：

- Web 服务
- 企业应用
- 数据处理
- 桌面软件
- CLI 工具

它不优先追求：

- 极端底层控制
- 模板元编程
- 类型级计算

## 安全但不过度复杂

Norm 借鉴现代语言的安全设计：

- 非空默认
- 强类型
- Result 错误模型
- 值语义

但拒绝将所有复杂性交给开发者。

例如 Norm 不采用 Rust 的所有权系统，因为应用开发通常更需要开发效率。

# 用户如何使用 Norm

一个简单程序：

```norm
void main() {
    String name = "Norm"
    print("Hello ${name}")
}
```

一个 Web 服务：

```norm
@Controller(path = "/users")
class UserController {
    @Get(path = "/{id}")
    User get(long id) {
        return service.find(id)
    }
}
```

一个业务模型：

```norm
class User {
    long id
    String name
    String? email
}
```

设计目标是让业务代码接近业务描述，而不是接近框架实现。
