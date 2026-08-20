# Norm GC Runtime Specification

## Memory Model

Norm uses managed memory.

Developers do not manually release objects.

## Allocation

Runtime categories:

- value storage
- managed objects
- shared Ref objects
- runtime metadata

## Optimization

The runtime may use:

- generational collection
- escape analysis
- copy-on-write
- scalar replacement

The language semantics remain value based.
