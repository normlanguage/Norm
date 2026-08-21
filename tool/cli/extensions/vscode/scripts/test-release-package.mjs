import assert from 'node:assert/strict';
import { chmodSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  releaseVersion,
  stageCli,
  targetExecutable,
  verifyCliVersion,
} from './release-package.mjs';

assert.equal(releaseVersion('0.1.0'), '0.1.0');
assert.throws(() => releaseVersion('v0.1.0'));
assert.throws(() => releaseVersion('0.1'));
assert.equal(targetExecutable('win32-x64'), 'norm.exe');
assert.equal(targetExecutable('linux-x64'), 'norm');
assert.equal(targetExecutable('darwin-arm64'), 'norm');
assert.throws(() => targetExecutable('darwin-x64'));

const root = mkdtempSync(join(tmpdir(), 'norm-release-'));
try {
  const binary = join(root, process.platform === 'win32' ? 'norm.cmd' : 'norm');
  writeFileSync(
    binary,
    process.platform === 'win32' ? '@echo off\r\necho norm 0.1.0\r\n' : '#!/bin/sh\necho norm 0.1.0\n',
  );
  chmodSync(binary, 0o755);
  verifyCliVersion(binary, '0.1.0');
  assert.throws(() => verifyCliVersion(binary, '0.1.1'));

  const extension = join(root, 'extension');
  mkdirSync(extension);
  const staged = stageCli(binary, 'win32-x64', extension);
  assert.equal(staged, join(extension, 'bin', 'norm.exe'));
  assert.equal(readFileSync(staged, 'utf8'), readFileSync(binary, 'utf8'));
} finally {
  rmSync(root, { recursive: true, force: true });
}

console.log('Norm release packaging tests succeeded.');
