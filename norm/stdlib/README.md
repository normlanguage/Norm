# Standard library

This directory is the source root for the standard library shipped with the compiler. `std/module.norm` constructs its `Module` descriptor. An export such as `math.integer` in module `std` identifies `std/math/integer.norm`.

Code that Norm can express belongs here. The builtin ABI belongs to `compiler`; runtime bridges, platform contracts, and their JDK implementation belong to their domain packages in `compiler`. They expose only the internal operations required to implement stable Norm APIs.

The standard library must not introduce Zig or expose host Java types through its public Norm API.
