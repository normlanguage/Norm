import assert from 'node:assert/strict';
import { test } from 'node:test';
import { measureStartup } from './measure-startup.mjs';

test('measures output and HTTP readiness of a real child process', async () => {
  const result = await measureStartup({
    command: process.execPath,
    args: ['-e', `const http = require('node:http'); console.error('initializing'); const server = http.createServer((req, res) => res.end('ready')); server.listen(0, '127.0.0.1', () => console.log('http://127.0.0.1:' + server.address().port + '/'));`],
    cwd: process.cwd(),
    timeoutMs: 10000,
  });
  assert.ok(result.firstOutputMs >= 0);
  assert.ok(result.httpReadyMs >= result.firstOutputMs);
  assert.equal(result.status, 200);
  assert.ok(result.output.some(event => event.stream === 'stderr' && event.text.includes('initializing')));
});

test('rejects an application which exits without becoming ready', async () => {
  await assert.rejects(measureStartup({ command: process.execPath, args: ['-e', 'process.exit(7)'], cwd: process.cwd(), timeoutMs: 10000 }), /exited.*7/);
});

test('bounds the lifetime of an application which never announces readiness', async () => {
  await assert.rejects(measureStartup({ command: process.execPath, args: ['-e', 'setInterval(() => {}, 1000)'], cwd: process.cwd(), timeoutMs: 300 }), /timed out/);
});
