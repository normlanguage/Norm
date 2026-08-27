# Standard library

This directory is the source root for the standard library shipped with the compiler. `std/module.norm` constructs its `Module` descriptor. An export such as `math.integer` in module `std` identifies `std/math/integer.norm`.

Code that Norm can express belongs here. The builtin ABI belongs to `tool/core`, runtime bridges belong to `tool/truffle-backend`, platform contracts belong to `tool/execution-api`, and their JDK implementation belongs to `tool/platform-jdk`. They expose only the internal operations required to implement stable Norm APIs.

The standard library must not introduce Zig or expose host Java types through its public Norm API.
