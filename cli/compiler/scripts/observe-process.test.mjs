import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { observeProcess } from './observe-process.mjs';

test('startup failure drains and reports both output streams', async () => {
  const child = spawn(process.execPath, ['-e',
    'process.stdout.write("MissingResourceRegistrationError: simplelogger.properties\\n"); process.stderr.write("details\\n"); process.exitCode = 172;'],
    { windowsHide: true });
  const observed = observeProcess(child, /READY (\S+)/, 5000);
  await assert.rejects(observed.ready, error => {
    assert.match(error.message, /172/);
    assert.match(error.message, /MissingResourceRegistrationError: simplelogger.properties/);
    assert.match(error.message, /details/);
    return true;
  });
  assert.match(observed.diagnostics(), /simplelogger.properties/);
});

test('recognizes fragmented readiness and retains later diagnostics', async () => {
  const child = spawn(process.execPath, ['-e',
    'process.stdout.write("REA"); setTimeout(() => { process.stdout.write("DY http://127.0.0.1:1234\\n"); process.stderr.write("after readiness\\n"); }, 20);'],
    { windowsHide: true });
  const closed = once(child, 'close');
  const observed = observeProcess(child, /READY (http:\/\/127\.0\.0\.1:\d+)\n/, 5000);
  assert.equal((await observed.ready)[1], 'http://127.0.0.1:1234');
  await closed;
  assert.match(observed.diagnostics(), /after readiness/);
});

test('timeout reports captured output and leaves process ownership with caller', async () => {
  const child = spawn(process.execPath, ['-e', 'console.log("booting"); setInterval(() => {}, 1000);'],
    { windowsHide: true });
  const closed = once(child, 'close');
  try {
    const observed = observeProcess(child, /READY (\S+)/, 1500);
    await assert.rejects(observed.ready, /timed out[\s\S]*booting/);
    assert.equal(child.exitCode, null);
  } finally {
    child.kill();
    await closed;
  }
});

test('reports failure to spawn without leaving an unhandled error', async () => {
  const child = spawn('norm-nonexistent-process-for-test', [], { windowsHide: true });
  const observed = observeProcess(child, /READY (\S+)/, 5000);
  await assert.rejects(observed.ready, /ENOENT/);
});
