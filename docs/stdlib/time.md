# 时间 API

时间库区分机器时间点、日历值、时区和时间长度，避免一个 DateTime 类型承担互不兼容的语义。

| 类型 | 含义 |
| --- | --- |
| Instant | UTC 时间线上的唯一时间点 |
| LocalDate | 不带时区的日历日期 |
| LocalTime | 不带日期和时区的钟表时间 |
| ZonedDateTime | Instant 在指定 TimeZone 中的表示 |
| Duration | 固定秒与纳秒长度 |
| Period | 年、月、日组成的日历跨度 |

```norm
Instant now = clock.now()
ZonedDateTime local = now.inZone(zone = TimeZone("Asia/Singapore"))
```

Clock 是显式依赖，业务代码不直接读取隐藏全局时钟，测试可以注入 FixedClock。

格式化和解析使用明确 pattern、locale 与 zone。解析没有 offset 的本地时间不能自动猜测系统时区。夏令时造成的不存在或重复本地时间返回结构化解析结果。

Duration 用于超时，Period 用于“一个月后”一类日历运算；两者不能隐式转换。

