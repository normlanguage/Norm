import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const repository = resolve(process.cwd(), '..', '..', '..', '..');
const cli = resolve(
  repository,
  'tool/cli/app/build/install/norm/bin',
  process.platform === 'win32' ? 'norm.bat' : 'norm',
);
const source = resolve(repository, 'norm/tests/class/02_parameter_identity.norm');
const command = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : cli;
const args =
  process.platform === 'win32'
    ? ['/d', '/c', 'call', cli, 'run', source]
    : ['run', source];
const result = spawnSync(command, args, { cwd: repository, encoding: 'utf8' });

if (result.error) throw result.error;
assert.equal(result.status, 0);
assert.equal(result.stderr, '');
assert.equal(result.stdout.replaceAll('\r\n', '\n').trimEnd(), '5\ntrue');
console.log('Norm CLI clean-output smoke test succeeded.');
