# Generational GC Complete Flow

## Collector Model

Norm runtime uses generational tracing GC.

Generations:

- Young
- Old

## Minor Collection

1. Stop mutator threads.
2. Scan young roots.
3. Trace reachable objects.
4. Copy survivors.
5. Update references.
6. Resume execution.

## Major Collection

1. Mark all reachable objects.
2. Process weak metadata.
3. Sweep unreachable objects.
4. Compact when required.
