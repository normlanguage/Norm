# Async Task and Future API

Norm async runtime is based on explicit tasks.

Example:

```norm
Task<User> task = async loadUser(id = id)
User user = await task
```

Core concepts:

- Task
- Future
- Executor
- Scheduler
- Cancellation

Async operations do not create hidden threads.
