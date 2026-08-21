# 密码学

Crypto 模块封装经过审查的密码学原语。标准库不鼓励自定义算法、手工拼装协议或使用普通 Random 生成密钥。

## Hash 与 HMAC

```norm
Digest digest = Hash.sha256(data = bytes)
Mac mac = Hmac.sha256(key = key, data = bytes)
```

hash 用于完整性与内容寻址，不用于存储密码。验证 MAC 使用恒定时间比较。

## 加密

高层 API 优先提供 AEAD，同时产生密文和认证标签。nonce 长度由算法固定，同一 key 下不得重复；API 可以自动生成 nonce 并把它与密文封装。

## 签名

签名算法在 key 类型中固定，sign 和 verify 不能通过不可信输入动态选择算法。公钥、私钥和证书使用不同类型，private key 由 Secret 容器保护。

## 算法生命周期

安全默认值可以随版本升级，但持久数据必须记录算法和参数版本。被弃用算法保留只读迁移能力并产生告警，新数据禁止继续使用。

底层 primitive 与协议级功能分模块发布，避免普通调用者误用不安全组合。

