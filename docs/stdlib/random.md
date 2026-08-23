# Random

Random 提供可重复的伪随机序列，用于模拟、抽样和测试；安全 token 与密钥必须使用 `SecureRandom`。

```norm
Random random = Random.seeded(seed: 42)
Integer die = random.integer(minimum: 1, maximumExclusive: 7)
Double sample = random.double()
```

## 范围

整数范围使用包含 minimum、不包含 maximum 的半开区间。minimum 大于等于 maximum 时返回参数错误。`Double()` 返回 `[0.0, 1.0)`。

## 可重复性

固定算法和版本的 seeded Random 必须产生相同序列，便于测试复现。算法升级使用新的明确版本，不能让库 minor 更新静默改变已有结果。

```norm
Random random = Random.algorithm(
    algorithm: RandomAlgorithm.Xoshiro256,
    seed: seed
)
```

Random 是有内部状态的 class；普通赋值保留同一生成器 identity，`copy()` 才创建状态相同的新生成器。跨线程访问同一生成器需要同步，但通常应为每个任务派生独立子流。

shuffle 和 sample 接受显式 Random 参数，使测试可以控制随机源。
