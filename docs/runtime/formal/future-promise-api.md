# Future Promise API

Conceptual API:

```
Future<T>
  await()
  cancel()
  then(Function)

Promise<T>
  resolve(value)
  reject(error)
```

Future represents a computation result, not a thread.
