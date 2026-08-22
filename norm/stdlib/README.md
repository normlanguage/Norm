# Standard library

This directory is the source root for the standard library shipped with the compiler. `module.norm` constructs its `Module` descriptor. An export such as `math.integer` in module `std` identifies `std/math/integer.norm`.

Code that Norm can express belongs here. VM primitives and platform adapters belong to `tool/core`; they expose only the operations required to implement stable Norm APIs.

The standard library must not introduce Zig or expose host Java types through its public Norm API.
