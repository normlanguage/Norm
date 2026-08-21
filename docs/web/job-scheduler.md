# 任务调度器

调度器按时间创建后台任务。它负责“何时触发”，任务执行器负责重试、幂等和结果记录。

```norm
Schedule cleanup = Schedule.cron(
    expression = "0 3 * * *",
    timeZone = TimeZone("Asia/Singapore")
)

scheduler.register(
    name = "cleanup-expired-sessions",
    schedule = cleanup,
    task = cleanupExpiredSessions
)
```

## 时间语义

cron 必须绑定时区。夏令时跳跃时，策略需要明确选择跳过、补跑或只运行一次。固定间隔调度使用单调时钟计算间隔，日历调度使用 wall clock。

## 多实例

多副本部署不能让每个实例都执行同一单例任务。分布式 scheduler 使用带租约的 leader 或存储原子 claim；租约到期可能造成重复，因此任务仍必须幂等。

## Misfire

服务停机期间错过触发点时，策略可选择 `Skip`、`RunOnce` 或 `CatchUp(limit)`。默认不能无限补跑。

每次执行保存 schedule 版本、计划时间、实际开始时间、结束状态和任务 id，便于审计和定位延迟。

