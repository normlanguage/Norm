import assert from 'node:assert/strict';
import { chmodSync, mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';

const require = createRequire(import.meta.url);
const { cliInvocation, resolveCliCommand } = require('../out/cli-command.cjs');
const fixture = mkdtempSync(join(tmpdir(), 'norm-vscode-'));
try {
  const versions = new Map();
  const probeVersion = async (command) => versions.get(command);
  const repository = join(fixture, 'repository');
  const developmentExtension = join(
    repository,
    'cli',
    'extensions',
    'vscode',
    'test-fixtures',
    'unbundled-extension',
  );
  const expected = join(
    repository,
    'cli',
    'compiler',
    'build',
    'install',
    'norm',
    'bin',
    process.platform === 'win32' ? 'norm.bat' : 'norm',
  );
  mkdirSync(dirname(expected), { recursive: true });
  writeFileSync(expected, '');
  chmodSync(expected, 0o755);
  versions.set(expected, '0.17.1-SNAPSHOT');
  const staleServer = join(
    developmentExtension,
    'server',
    'bin',
    process.platform === 'win32' ? 'norm.bat' : 'norm',
  );
  mkdirSync(dirname(staleServer), { recursive: true });
  writeFileSync(staleServer, '');
  chmodSync(staleServer, 0o755);
  versions.set(staleServer, '0.16.0-SNAPSHOT');
  const developmentOptions = {
    configured: '',
    workspacePath: repository,
    extensionPath: developmentExtension,
    extensionVersion: '0.17.1',
    development: true,
  };
  const development = await resolveCliCommand(developmentOptions, probeVersion);
  assert.equal(development.selected?.command, expected);
  assert.equal(development.selected?.version, '0.17.1-SNAPSHOT');
  assert.equal(development.selected?.source, 'workspace');

  const configured = await resolveCliCommand(
    { ...developmentOptions, configured: expected },
    probeVersion,
  );
  assert.equal(configured.selected?.source, 'configured');

  const missingConfigured = await resolveCliCommand(
    { ...developmentOptions, configured: 'definitely-missing-norm-cli' },
    probeVersion,
  );
  assert.equal(missingConfigured.selected?.command, expected);
  assert.equal(missingConfigured.rejected[0].reason, 'not-found');
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
  const bundled = join(
    bin,
    'norm',
    'bin',
    process.platform === 'win32' ? 'norm.bat' : 'norm',
  );
  mkdirSync(dirname(bundled), { recursive: true });
  writeFileSync(bundled, '');
  chmodSync(bundled, 0o755);
  versions.set(bundled, '0.17.1');
  const bundledJvm = join(
    packagedExtension,
    'server',
    'bin',
    process.platform === 'win32' ? 'norm.bat' : 'norm',
  );
  mkdirSync(dirname(bundledJvm), { recursive: true });
  writeFileSync(bundledJvm, '');
  chmodSync(bundledJvm, 0o755);
  versions.set(bundledJvm, '0.16.0-SNAPSHOT');
  const external = join(fixture, process.platform === 'win32' ? 'external.exe' : 'external');
  writeFileSync(external, '');
  chmodSync(external, 0o755);
  versions.set(external, '0.16.0');
  const productionOptions = {
    configured: external,
    workspacePath: repository,
    extensionPath: packagedExtension,
    extensionVersion: '0.17.1',
    development: false,
  };
  const production = await resolveCliCommand(productionOptions, probeVersion);
  assert.equal(production.selected?.command, bundled);
  assert.equal(production.selected?.version, '0.17.1');
  assert.equal(production.selected?.source, 'bundled');
  assert.deepEqual(
    production.rejected
      .filter(({ source }) => source === 'configured')
      .map(({ source, version }) => [source, version]),
    [
      ['configured', '0.16.0'],
    ],
  );

  versions.set(expected, '0.17.1');
  const productionPrefersBundled = await resolveCliCommand(
    { ...productionOptions, configured: '' },
    probeVersion,
  );
  assert.equal(productionPrefersBundled.selected?.command, bundled);
  assert.equal(productionPrefersBundled.selected?.source, 'bundled');

  versions.set(expected, '0.17.2-SNAPSHOT');
  const productionAcceptsCompatibleWorkspacePatch = await resolveCliCommand(
    { ...productionOptions, configured: '' },
    probeVersion,
  );
  assert.equal(productionAcceptsCompatibleWorkspacePatch.selected?.command, expected);
  assert.equal(productionAcceptsCompatibleWorkspacePatch.selected?.version, '0.17.2-SNAPSHOT');
  assert.equal(productionAcceptsCompatibleWorkspacePatch.selected?.source, 'workspace');

  versions.set(expected, '0.17.0');
  const productionRejectsOlderWorkspacePatch = await resolveCliCommand(
    { ...productionOptions, configured: '' },
    probeVersion,
  );
  assert.equal(productionRejectsOlderWorkspacePatch.selected?.command, bundled);
  assert.equal(productionRejectsOlderWorkspacePatch.selected?.source, 'bundled');

  versions.set(external, '0.17.1');
  const compatibleConfigured = await resolveCliCommand(productionOptions, probeVersion);
  assert.equal(compatibleConfigured.selected?.command, external);
  assert.equal(compatibleConfigured.selected?.source, 'configured');

  versions.delete(external);
  versions.delete(bundled);
  versions.set(expected, '0.16.0-SNAPSHOT');
  const incompatible = await resolveCliCommand(productionOptions, probeVersion);
  assert.equal(incompatible.selected, undefined);
  assert.ok(incompatible.rejected.some(({ reason }) => reason === 'version-unavailable'));
} finally {
  rmSync(fixture, { recursive: true, force: true });
}
console.log('Norm CLI discovery tests succeeded.');
