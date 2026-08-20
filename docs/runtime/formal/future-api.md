# Async Future API

## Task

```norm
Task<User> task = async loadUser(id = id)
User user = await task
```

## Future States

Created -> Scheduled -> Running -> Waiting -> Completed

Failed and Cancelled are terminal states.

## API

```norm
interface Future<T> {
    T await()
    boolean isCompleted()
    void cancel()
}
```
