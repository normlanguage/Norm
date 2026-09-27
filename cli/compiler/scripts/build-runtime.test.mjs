import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync, chmodSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { buildRuntime } from './build-runtime.mjs';

test('runtime build invokes the repository Gradle wrapper', () => {
  const root = mkdtempSync(join(tmpdir(), 'norm-runtime-wrapper-'));
  try {
    const wrapper = join(root, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
    const record = join(root, 'invocation.txt');
    const script = process.platform === 'win32'
      ? `@echo off\r\necho %* > "${record}"\r\n`
      : `#!/bin/sh\nprintf '%s\\n' "$*" > '${record}'\n`;
    writeFileSync(wrapper, script);
    if (process.platform !== 'win32') chmodSync(wrapper, 0o755);
    assert.equal(buildRuntime(root), join(root, 'build', 'compiler', 'norm-runtime'));
    assert.match(readFileSync(record, 'utf8'), /:compiler:installRuntimeDist --no-daemon/);
    const runtime = buildRuntime(root, '0.23.0');
    assert.equal(runtime, join(root, 'build', 'compiler', 'norm-runtime'));
    assert.ok(existsSync(record));
    assert.match(readFileSync(record, 'utf8'), /:compiler:installRuntimeDist -PnormVersion=0\.23\.0 --no-daemon/);
    assert.throws(() => buildRuntime(root, '0.23'), /Invalid release version/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
