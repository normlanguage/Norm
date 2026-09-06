import assert from 'node:assert/strict';

export function verifyNativeMetrics(size, raw) {
  const measurements = {
    imageBytes: raw.image_details?.total_bytes,
    codeBytes: raw.image_details?.code_area?.bytes,
    heapBytes: raw.image_details?.image_heap?.bytes,
    reachableMethods: raw.analysis_results?.methods?.reachable
  };
  for (const [name, value] of Object.entries(measurements)) {
    assert.ok(Number.isSafeInteger(value) && value > 0, `Invalid GraalVM metric: ${name}`);
    assert.equal(size[name], value, `GraalVM metric mismatch: ${name}`);
  }
}
