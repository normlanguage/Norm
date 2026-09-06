import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { test } from 'node:test';
import { measureHttp } from './measure-http.mjs';

test('measures all responses after warmup using a real HTTP server', async () => {
  let requests = 0;
  const server = createServer((request, response) => { requests++; response.end('ok'); });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  try {
    const result = await measureHttp(`http://127.0.0.1:${server.address().port}/`, 'ok', { requests: 20, concurrency: 3, warmup: 2 });
    assert.equal(requests, 22);
    assert.equal(result.requests, 20);
    assert.ok(result.elapsedMs > 0);
    assert.ok(result.requestsPerSecond > 0);
    assert.ok(result.p99Ms >= result.p95Ms && result.p95Ms >= result.p50Ms);
  } finally {
    server.closeAllConnections();
    server.close();
    await once(server, 'close');
  }
});

test('rejects incorrect responses instead of counting them as throughput', async () => {
  const server = createServer((request, response) => { response.statusCode = 500; response.end('ok'); });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  try {
    await assert.rejects(measureHttp(`http://127.0.0.1:${server.address().port}/`, 'ok', { requests: 3, concurrency: 2, warmup: 0 }));
  } finally {
    server.closeAllConnections();
    server.close();
    await once(server, 'close');
  }
});

test('rejects invalid measurement sizes', async () => {
  await assert.rejects(measureHttp('http://127.0.0.1:1/', 'ok', { requests: 0 }));
  await assert.rejects(measureHttp('http://127.0.0.1:1/', 'ok', { concurrency: -1 }));
});
