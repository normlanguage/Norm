import assert from 'node:assert/strict';
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { stageServerDistribution } from './build-server.mjs';
import { localPackageName } from './package-local.mjs';
import { packageIgnore } from './vsce-package.mjs';

assert.equal(localPackageName('0.7.0'), 'norm-language-support-0.7.0-local.vsix');
assert.throws(() => localPackageName('0.7'));
assert.equal(packageIgnore('out/test/**\n'), 'out/test/**\n');
assert.equal(packageIgnore('out/test/**\n', 'bin'), 'out/test/**\n\nbin/**\n');
assert.throws(() => packageIgnore('out/test/**\n', 'other'));

const root = mkdtempSync(join(tmpdir(), 'norm-local-package-'));
try {
  const distribution = join(root, 'distribution');
  const extension = join(root, 'extension');
  mkdirSync(join(distribution, 'bin'), { recursive: true });
  mkdirSync(join(distribution, 'lib'), { recursive: true });
  mkdirSync(join(extension, 'server'), { recursive: true });
  mkdirSync(join(extension, 'bin', 'win32-x64'), { recursive: true });
  writeFileSync(join(distribution, 'bin', 'norm'), 'launcher');
  writeFileSync(join(distribution, 'lib', 'core.jar'), 'artifact');
  writeFileSync(join(extension, 'server', 'stale'), 'stale');
  writeFileSync(join(extension, 'bin', 'win32-x64', 'norm.exe'), 'release');

  const staged = stageServerDistribution(distribution, extension);

  assert.equal(staged, join(extension, 'server'));
  assert.equal(readFileSync(join(staged, 'bin', 'norm'), 'utf8'), 'launcher');
  assert.equal(readFileSync(join(staged, 'lib', 'core.jar'), 'utf8'), 'artifact');
  assert.throws(() => readFileSync(join(staged, 'stale')));
  assert.equal(
    readFileSync(join(extension, 'bin', 'win32-x64', 'norm.exe'), 'utf8'),
    'release',
  );
} finally {
  rmSync(root, { recursive: true, force: true });
}

console.log('Norm local packaging tests succeeded.');
