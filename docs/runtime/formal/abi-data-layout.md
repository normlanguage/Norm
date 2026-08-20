# Norm ABI Data Layout

## Object Header

Every managed object contains:

```
ObjectHeader
 ├─ TypeMetadata*
 ├─ GCFlags
 ├─ IdentityFlags
 └─ RuntimeData
```

## Value Layout

Value types are lowered as inline storage when possible.

## Reference Layout

Ref<T> is a managed pointer with GC tracking metadata.
