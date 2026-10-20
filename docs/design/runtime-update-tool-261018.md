---
title: Runtime Update Tool
description: Runtime update objects, structured hot-load results, callbacks, and standard-library entry points
---

# Runtime Update Tool

The public runtime update contract is defined by [`std.runtime.Runtime`](../../norm/stdlib/std/runtime/updates.norm), [`std.runtime`](../../norm/stdlib/std/runtime/updates.norm), and [`std.system`](../../norm/stdlib/std/system/runtime.norm). Applications obtain the current execution runtime through `currentSystem().runtime()`. Publication results contain declaration-level changes, and `runtime.onUpdate` delivers requested, published, rejected, failed, adoption-pending, and adopted transitions through a callback. The runtime implementation is covered by [`ExecutionUpdates`](../../cli/compiler/src/main/java/dev/w0fv1/norm/execution/ExecutionUpdates.java), [`LiveApplicationSession`](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/LiveApplicationSession.java), and [`RuntimeUpdateIntrinsicDispatcher`](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/RuntimeUpdateIntrinsicDispatcher.java).

Declaration differences are produced by [`RuntimeChangeAnalyzer`](../../cli/compiler/src/main/java/dev/w0fv1/norm/execution/RuntimeChangeAnalyzer.java) from authoring identity, semantic shape, annotation summaries, and content summaries in the compiled artifacts. `ExecutionUpdateResult.changes` is the structured Java API result; the standard-library `RuntimeChange` is the same result projected into Norm, and event `changes` are stable summaries suitable for display.

Preview entry points are [`ExecutionUpdatePreview`](../../cli/compiler/src/main/java/dev/w0fv1/norm/execution/ExecutionUpdatePreview.java), [`ExecutionUpdates.preview`](../../cli/compiler/src/main/java/dev/w0fv1/norm/execution/ExecutionUpdates.java), and the standard-library `Runtime.previewUpdate` and `UpdateBatch.preview`. Preview and publication share the `LiveApplicationSession` compilation queue and change analysis. A preview does not invoke the execution backend, update the published baseline, or emit a publication event. A batch preview retains its candidate revision; commit revalidates the input snapshot and rejects stale source changes so callers must preview again.

Validation entry points:

- [`RuntimeChangeAnalyzerTest`](../../cli/compiler/src/test/java/dev/w0fv1/norm/execution/RuntimeChangeAnalyzerTest.java) verifies before and after shapes for types, constructors, and functions;
- [`ApplicationSessionTest`](../../cli/compiler/src/test/java/dev/w0fv1/norm/application/ApplicationSessionTest.java) verifies real application updates, structured changes, and event order;
- [`RuntimeUpdatesTest`](../../cli/compiler/src/test/java/dev/w0fv1/norm/stdlib/RuntimeUpdatesTest.java) verifies the standard-library contract returns a uniform unavailable diagnostic without an update coordinator.
