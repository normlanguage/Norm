# Router Annotation Reflection Implementation

Routing uses annotation metadata.

Flow:

Controller annotation
 -> reflection scan
 -> route registration
 -> runtime router tree
 -> request dispatch

Example:

```norm
@Get(path="/users/{id}")
User getUser(long id)
```

No hidden proxy generation is required.
