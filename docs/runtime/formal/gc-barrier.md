# GC Barrier and Generational Collector

## Heap Generations

Norm runtime uses generational collection concepts.

Young objects are collected frequently. Long lived objects are promoted.

## Write Barrier

Reference updates record old-to-young references.

## Ref Integration

Ref<T> participates in GC tracing and keeps object identity alive.
