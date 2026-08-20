# Norm Garbage Collection Design

Norm uses managed memory.

Goals:

- predictable application development
- low developer burden
- support for Ref<T>
- support for reflection

The runtime may use generational and tracing GC techniques.
