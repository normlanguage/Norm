import assert from 'node:assert/strict';
import { chmodSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import {
  releaseTargets,
  releaseVersion,
  stageCliBundle,
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

const extensionRoot = resolve(import.meta.dirname, '..');
const extensionPackage = JSON.parse(readFileSync(join(extensionRoot, 'package.json'), 'utf8'));
assert.equal(extensionPackage.icon, 'images/norm-256.png');
assert.deepEqual(extensionPackage.contributes.languages[0].icon, {
  light: './images/norm-file.png',
  dark: './images/norm-file.png',
});
for (const asset of ['norm-256.png', 'norm-file.png']) {
  assert.deepEqual(
    [...readFileSync(join(extensionRoot, 'images', asset)).subarray(0, 8)],
    [137, 80, 78, 71, 13, 10, 26, 10],
  );
}

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

  const binaries = join(root, 'binaries');
  for (const { target } of releaseTargets) {
    const directory = join(binaries, `native-${target}`);
    mkdirSync(directory, { recursive: true });
    writeFileSync(join(directory, targetExecutable(target)), target);
  }
  const extension = join(root, 'extension');
  mkdirSync(extension);
  const staged = stageCliBundle(binaries, extension);
  assert.deepEqual(
    staged,
    releaseTargets.map(({ target, executable }) =>
      join(extension, 'bin', target, executable),
    ),
  );
  for (const { target } of releaseTargets) {
    assert.equal(
      readFileSync(join(extension, 'bin', target, targetExecutable(target)), 'utf8'),
      target,
    );
  }
} finally {
  rmSync(root, { recursive: true, force: true });
}

console.log('Norm release packaging tests succeeded.');
