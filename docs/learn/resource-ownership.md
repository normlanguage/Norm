# 81 Resource ownership

A subscription created inside a `ResourceOwner` context registers with that owner. Explicit `close()` releases it early and removes it from the owner's resource list; a host such as a GUI component can later clean up resources it still owns.

`withContext` provides the owner while its callback runs. The returned subscription retains that ownership relation, so closing it after the callback exits still removes the registration. This example verifies registration and early release, not host destruction.

<<< ../../norm/tests/docs/tour/resource_ownership.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/resource_ownership.out{text}

Try it: Create a second subscription without closing it and inspect the owner's list.

Precise rules: [Reference](/spec/execution-context).
