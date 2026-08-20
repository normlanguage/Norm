# Generational GC Collector

Pseudo algorithm:

```
collectYoung()
  roots = scanRoots()
  markYoungReachable(roots)
  copySurvivors()
  promoteOldObjects()
```

Major collection traces the complete heap graph and reclaims unreachable objects.
