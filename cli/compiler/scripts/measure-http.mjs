import assert from 'node:assert/strict';

export async function measureHttp(url, expected, { requests = 4000, concurrency = 4, warmup = 100 } = {}) {
  assert.ok(Number.isSafeInteger(requests) && requests > 0);
  assert.ok(Number.isSafeInteger(concurrency) && concurrency > 0 && concurrency <= requests);
  assert.ok(Number.isSafeInteger(warmup) && warmup >= 0);
  async function request() {
    const started = performance.now();
    const response = await fetch(url, { signal: AbortSignal.timeout(10000) });
    const body = await response.text();
    assert.equal(response.status, 200, body);
    assert.equal(body, expected);
    return performance.now() - started;
  }
  for (let index = 0; index < warmup; index++) await request();
  const durations = [];
  let next = 0;
  const started = performance.now();
  const workers = await Promise.allSettled(Array.from({ length: concurrency }, async () => {
    while (next++ < requests) durations.push(await request());
  }));
  const elapsedMs = performance.now() - started;
  const failures = workers.filter(worker => worker.status === 'rejected').map(worker => worker.reason);
  if (failures.length) throw new AggregateError(failures, 'HTTP measurement failed');
  assert.equal(durations.length, requests);
  durations.sort((left, right) => left - right);
  return {
    requests, concurrency, warmup, elapsedMs,
    requestsPerSecond: requests * 1000 / elapsedMs,
    p50Ms: durations[Math.ceil(requests * 0.50) - 1],
    p95Ms: durations[Math.ceil(requests * 0.95) - 1],
    p99Ms: durations[Math.ceil(requests * 0.99) - 1],
  };
}
