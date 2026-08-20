# Generational GC Algorithm

Norm runtime uses a generational collector design.

## Heap

Young generation stores short-lived objects.
Old generation stores promoted objects.

## Collection

Minor GC:
- scan young objects
- copy survivors
- update references

Major GC:
- trace reachable objects
- reclaim unused memory

## Barrier

Write barriers record old-to-young references.
