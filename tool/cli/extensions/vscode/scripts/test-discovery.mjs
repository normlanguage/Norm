import assert from 'node:assert/strict';
import { chmodSync, mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

const require = createRequire(import.meta.url);
const { cliInvocation, resolveCliCommand } = require('../out/cli-command.cjs');
const repository = resolve(process.cwd(), '..', '..', '..', '..');
const extension = resolve(repository, 'tool', 'cli', 'extensions', 'vscode');
const developmentExtension = resolve(extension, 'test-fixtures', 'unbundled-extension');
const expected = resolve(
  repository,
  'tool',
  'cli',
  'app',
  'build',
  'install',
  'norm',
  'bin',
  process.platform === 'win32' ? 'norm.bat' : 'norm',
);

assert.equal(resolveCliCommand('', repository, developmentExtension), expected);
assert.equal(resolveCliCommand(expected, repository, developmentExtension), expected);
assert.equal(
  resolveCliCommand('definitely-missing-norm-cli', repository, developmentExtension),
  undefined,
);
const invocation = cliInvocation(expected, ['run', 'source file.norm']);
if (process.platform === 'win32') {
  assert.equal(invocation.command, process.env.ComSpec ?? 'cmd.exe');
  assert.deepEqual(invocation.args, ['/d', '/c', 'call', expected, 'run', 'source file.norm']);
} else {
  assert.equal(invocation.command, expected);
  assert.deepEqual(invocation.args, ['run', 'source file.norm']);
}

const packagedExtension = mkdtempSync(join(tmpdir(), 'norm-vscode-'));
try {
  const bin = join(packagedExtension, 'bin');
  mkdirSync(bin);
  const bundled = join(bin, process.platform === 'win32' ? 'norm.exe' : 'norm');
  writeFileSync(bundled, '');
  chmodSync(bundled, 0o755);
  assert.equal(resolveCliCommand('', undefined, packagedExtension), bundled);
  const previous = process.env.NORM_CLI;
  process.env.NORM_CLI = bundled;
  assert.equal(resolveCliCommand('', repository, developmentExtension), bundled);
  if (previous === undefined) delete process.env.NORM_CLI;
  else process.env.NORM_CLI = previous;
} finally {
  rmSync(packagedExtension, { recursive: true, force: true });
}
console.log('Norm CLI discovery tests succeeded.');
