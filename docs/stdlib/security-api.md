# Security API

安全模块提供密钥材料、恒定时间比较与密码派生等容易误用的基础能力。它不设计自定义密码算法，也不把普通 String 当作秘密容器。

## `Secret<T>`

```norm
Secret<Bytes> key = Secret<Bytes>(value: randomBytes)
```

`Secret<T>` 的默认格式化结果始终是遮蔽文本，不能被字符串模板意外输出。显式读取秘密需要受限回调或解封操作，生命周期结束后实现应尽力清除可控内存。

## 比较与随机数

```norm
Boolean equal = Security.constantTimeEquals(
    left: expectedMac,
    right: receivedMac
)

Bytes nonce = SecureRandom.bytes(count: 32)
```

安全 token、salt、nonce 和 key 必须使用 `SecureRandom`。普通伪随机 API只用于模拟、采样和测试。

## 密码

密码存储 API 接收算法配置并返回带版本的编码结果：

```norm
PasswordHash hash = Password.hash(
    password: password,
    policy: PasswordPolicy.recommended()
)
```

验证结果应同时说明是否需要按新策略重哈希。算法默认值可以随库版本加强，但读取旧格式必须保持兼容。

加密、签名和证书属于各自模块；Security API 只提供共同的安全值类型和原则。

