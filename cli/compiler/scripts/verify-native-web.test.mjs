import assert from 'node:assert/strict';
import { existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { test } from 'node:test';
import { verifyNativeWeb } from './verify-native-web.mjs';

test('preserves the failed build directory and original failure for reproduction', async t => {
  const repository = resolve(import.meta.dirname, '../../..');
  const failure = new Error('fixture build failure');
  let directory;
  t.after(() => { if (directory) rmSync(directory, { recursive: true, force: true }); });
  await assert.rejects(verifyNativeWeb(repository, (args, expected, cwd) => {
    directory = cwd;
    assert.ok(readFileSync(args[1], 'utf8').includes('native-verification'));
    writeFileSync(resolve(directory, 'failure-artifact'), 'reproduce-me');
    throw failure;
  }), error => error === failure);
  assert.ok(existsSync(resolve(directory, 'web.norm')), 'Failed source must survive');
  assert.equal(readFileSync(resolve(directory, 'failure-artifact'), 'utf8'), 'reproduce-me');
});
