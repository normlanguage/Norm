# 时间 API

时间库区分 UTC 时间点、固定长度、时钟能力和后续日历类型，避免用一个 DateTime 类型混合不同语义。

## 基础类型

`Instant` 表示 UTC 时间线上的唯一时间点，由 epoch second 与 0..999,999,999 的 nanosecond adjustment 组成。`Duration` 表示固定秒数与相同范围的 nanosecond adjustment。两者分别通过 `instant` 与 `duration` 工厂建立 canonical value；无效 adjustment 抛出 `TimeException`。

```norm
Duration timeout = duration(seconds: 5, nanoseconds: 0)
Instant epoch = instant(epochSecond: 0, nanosecond: 0)
```

完整声明以 [`std.time`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/time/core.norm) 为准。

## Clock

业务函数接收显式 `Clock`，应用组合根通过 `systemClock()` 取得宿主时钟。测试平台可以注入 fixed clock，而不修改业务代码或全局状态。

```norm
Clock clock = systemClock()
Instant now = clock.now()
```

读取时钟失败抛出带稳定 code、operation 与 reason 的 `TimeException`。日历日期、时区、`Period`、格式化与解析在各自 API 落地时复用这些基础类型。
