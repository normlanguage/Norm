# Async Executor State Machine

Task states:

```
Created -> Scheduled -> Running -> Waiting -> Completed
                         |
                     Cancelled
```

Executor owns queues and workers.
