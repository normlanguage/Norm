# Norm GC Tracing Algorithm

## Mark Phase

```
collect():
  roots = scanRoots()
  for root in roots:
      mark(root)

mark(object):
  if object.marked:
      return
  object.marked = true
  for reference in object.references:
      mark(reference)
```

## Sweep Phase

Unreachable objects are reclaimed or compacted.
