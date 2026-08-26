import assert from 'node:assert/strict';
import { chmodSync, mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';

const require = createRequire(import.meta.url);
const { cliInvocation, resolveCliCommand } = require('../out/cli-command.cjs');
const fixture = mkdtempSync(join(tmpdir(), 'norm-vscode-'));
try {
  const repository = join(fixture, 'repository');
  const developmentExtension = join(
    repository,
    'tool',
    'cli',
    'extensions',
    'vscode',
    'test-fixtures',
    'unbundled-extension',
  );
  const expected = join(
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
  mkdirSync(dirname(expected), { recursive: true });
  writeFileSync(expected, '');
  chmodSync(expected, 0o755);
  const staleServer = join(
    developmentExtension,
    'server',
    'bin',
    process.platform === 'win32' ? 'norm.bat' : 'norm',
  );
  mkdirSync(dirname(staleServer), { recursive: true });
  writeFileSync(staleServer, '');
  chmodSync(staleServer, 0o755);
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

  const packagedExtension = join(fixture, 'packaged-extension');
  const target = `${process.platform}-${process.arch}`;
  const bin = join(packagedExtension, 'bin', target);
  mkdirSync(bin, { recursive: true });
  const bundled = join(bin, process.platform === 'win32' ? 'norm.exe' : 'norm');
  writeFileSync(bundled, '');
  chmodSync(bundled, 0o755);
  const bundledJvm = join(
    packagedExtension,
    'server',
    'bin',
    process.platform === 'win32' ? 'norm.bat' : 'norm',
  );
  mkdirSync(dirname(bundledJvm), { recursive: true });
  writeFileSync(bundledJvm, '');
  chmodSync(bundledJvm, 0o755);
  assert.equal(resolveCliCommand('', undefined, packagedExtension), bundledJvm);
  const external = join(fixture, process.platform === 'win32' ? 'external.exe' : 'external');
  writeFileSync(external, '');
  chmodSync(external, 0o755);
  assert.equal(resolveCliCommand('', undefined, packagedExtension), bundledJvm);
  assert.equal(resolveCliCommand(external, undefined, packagedExtension), external);

  rmSync(join(packagedExtension, 'server'), { recursive: true, force: true });
  assert.equal(resolveCliCommand('', undefined, packagedExtension), bundled);
} finally {
  rmSync(fixture, { recursive: true, force: true });
}
console.log('Norm CLI discovery tests succeeded.');
