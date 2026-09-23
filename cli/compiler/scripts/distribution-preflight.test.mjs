import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { requirements, probeDebian, probeFedora, makeReport, renderReport, runCommand, validateEnvironment } from './distribution-preflight.mjs';

const digest = 'a'.repeat(64);
const artifact = (coordinate, components = [coordinate]) => {
  const [group, name, version] = coordinate.split(':');
  return { file: `${name}-${version}.jar`, group, artifact: name, version, components, sha256: digest };
};
const catalog = () => ({
  schemaVersion: 1,
  artifacts: [artifact('test:root:1', ['test:root:1', 'test:merged:1']), artifact('test:child:2')],
  roots: ['test:root:1'],
  dependencies: { 'test:root:1': ['test:merged:1', 'test:child:2'], 'test:merged:1': [], 'test:child:2': [] },
  purposes: { execution: ['test:root:1'], hosted: [], tooling: [] },
});
const ok = (stdout = '') => ({ status: 0, stdout, stderr: '', error: null });
const fail = (stdout = '', stderr = '') => ({ status: 1, stdout, stderr, error: null });
const req = (kind = 'jar') => ({ coordinate: 'test:lib:1', group: 'test', artifact: 'lib', version: '1', kind, purposes: ['execution'] });
const temporary = (t) => { const dir = mkdtempSync(join(tmpdir(), 'norm-distribution-')); t.after(() => rmSync(dir, { recursive: true, force: true })); return dir; };
const candidate = (dir, version = '1') => {
  const base = join(dir, 'repo', 'test', 'lib', version);
  mkdirSync(base, { recursive: true });
  writeFileSync(join(base, `lib-${version}.pom`), '<project/>');
  writeFileSync(join(base, `lib-${version}.jar`), 'fixture bytes');
  return base;
};
const debianOwner = (command, args) => command === 'dpkg-query' && args[0] === '-S'
  ? ok(`libfixture-java: ${args.at(-1)}\n`)
  : ok('libfixture-java\t1-1\tall\tfixture\t1-1\tinstalled\n');

test('extracts the logical closure including merged components', () => {
  const rows = requirements(catalog());
  assert.deepEqual(rows.map(x => x.coordinate), ['test:child:2', 'test:merged:1', 'test:root:1']);
  assert.ok(rows.every(x => x.kind === 'jar'));
  assert.deepEqual(rows.find(x => x.artifact === 'child').purposes, ['execution']);
});

test('input key order cannot change the sorted requirements', () => {
  const input = catalog();
  input.dependencies = Object.fromEntries(Object.entries(input.dependencies).reverse());
  input.artifacts.reverse();
  assert.deepEqual(requirements(input), requirements(catalog()));
});

test('metadata-only graph nodes are not silently dropped or classified as JARs', () => {
  const input = catalog();
  input.dependencies['test:root:1'].push('test:bom:1');
  input.dependencies['test:bom:1'] = [];
  assert.equal(requirements(input).find(x => x.artifact === 'bom').kind, 'metadata');
});

test('unknown schema versions are rejected', () => assert.throws(() => requirements({ ...catalog(), schemaVersion: 2 }), /schema/i));
test('truncated graphs are rejected', () => { const input = catalog(); delete input.dependencies['test:child:2']; assert.throws(() => requirements(input), /absent/i); });
test('overlapping physical ownership is rejected', () => { const input = catalog(); input.artifacts.push(artifact('test:merged:1')); assert.throws(() => requirements(input), /owner/i); });
test('a primary component must be owned by its artifact', () => { const input = catalog(); input.artifacts[0].components = ['test:merged:1']; assert.throws(() => requirements(input), /primary/i); });
test('path traversal coordinates are rejected', () => { const input = catalog(); input.dependencies['../evil:x:1'] = []; assert.throws(() => requirements(input), /coordinate/i); });
test('invalid artifact digests are rejected', () => { const input = catalog(); input.artifacts[0].sha256 = 'no'; assert.throws(() => requirements(input), /digest/i); });
test('unreachable dependency nodes are rejected', () => { const input = catalog(); input.dependencies['test:stray:1'] = []; assert.throws(() => requirements(input), /unreachable/i); });
test('every root needs an explicit purpose', () => { const input = catalog(); input.purposes.execution = []; assert.throws(() => requirements(input), /purpose/i); });

test('Debian candidates record actual package ownership and byte hashes', t => {
  const dir = temporary(t); candidate(dir);
  const result = probeDebian(req(), { repository: join(dir, 'repo'), command: debianOwner });
  assert.equal(result.status, 'installed-candidate');
  assert.equal(result.candidates[0].versionDirectory, '1');
  assert.equal(result.candidates[0].files.length, 2);
  assert.ok(result.candidates[0].files.every(x => /^[a-f0-9]{64}$/.test(x.sha256)));
  assert.equal(result.candidates[0].files[0].owners[0].sourcePackage, 'fixture');
  assert.equal(result.compatibility, 'not-tested');
});

test('Debian version aliases are evidence, not compatibility claims', t => {
  const dir = temporary(t); candidate(dir, 'debian');
  const result = probeDebian(req(), { repository: join(dir, 'repo'), command: debianOwner });
  assert.equal(result.status, 'installed-candidate');
  assert.equal(result.candidates[0].versionDirectory, 'debian');
  assert.equal(result.candidates[0].matchesRequestedVersionDirectory, false);
  assert.equal(result.compatibility, 'not-tested');
});

test('a missing Debian artifact is not declared missing from the distribution', t => {
  const dir = temporary(t); mkdirSync(join(dir, 'repo'));
  const result = probeDebian(req(), { repository: join(dir, 'repo'), command: debianOwner });
  assert.equal(result.status, 'not-installed');
  assert.equal(result.repositoryAvailability, 'not-queried');
});

test('missing system Maven repository is a probe error', t => {
  const dir = temporary(t);
  assert.equal(probeDebian(req(), { repository: join(dir, 'absent'), command: debianOwner }).status, 'probe-error');
});

test('unowned JARs cannot satisfy system package provenance', t => {
  const dir = temporary(t); candidate(dir);
  const result = probeDebian(req(), { repository: join(dir, 'repo'), command: () => fail('', 'dpkg-query: no path found matching pattern') });
  assert.equal(result.status, 'unverified-candidate');
});

test('a POM without its required JAR is not an installed runtime candidate', t => {
  const dir = temporary(t); const base = candidate(dir); rmSync(join(base, 'lib-1.jar'));
  assert.equal(probeDebian(req(), { repository: join(dir, 'repo'), command: debianOwner }).status, 'incomplete-candidate');
});

test('symlink targets are hashed and must be package-owned', t => {
  const dir = temporary(t); const base = candidate(dir);
  const jar = join(base, 'lib-1.jar'); rmSync(jar);
  const target = join(dir, 'system.jar'); writeFileSync(target, 'actual installed bytes'); symlinkSync(target, jar);
  const result = probeDebian(req(), { repository: join(dir, 'repo'), command: debianOwner });
  assert.equal(result.candidates[0].files.find(x => x.path.endsWith('.jar')).realPath, target);
});

test('Debian errors are not converted into successful empty queries', t => {
  const dir = temporary(t); candidate(dir);
  const result = probeDebian(req(), { repository: join(dir, 'repo'), command: () => ({ status: null, stdout: '', stderr: '', error: 'ENOENT' }) });
  assert.equal(result.status, 'probe-error');
});

test('Fedora matches installed Maven capabilities, not guessed package names', () => {
  const calls = [];
  const command = (name, args) => {
    calls.push([name, args]);
    if (args.includes('--whatprovides')) return ok('fixture\t0\t2\t1.fc45\tnoarch\tfixture-2-1.fc45.src.rpm\n');
    return ok('mvn(test:lib) = 2\nmvn(test:other) = 5\n');
  };
  const result = probeFedora(req(), { command });
  assert.equal(result.status, 'installed-candidate');
  assert.deepEqual(result.candidates[0].artifactVersions, ['2']);
  assert.equal(result.candidates[0].matchesRequestedArtifactVersion, false);
  assert.equal(result.compatibility, 'not-tested');
  assert.ok(calls.some(([, args]) => args.includes('mvn(test:lib)')));
});

test('Fedora no installed provider does not imply repository absence', () => {
  const result = probeFedora(req(), { command: (_program, args) => args[0] === '-qa' ? ok() : fail('no package provides mvn(test:lib)\n') });
  assert.equal(result.status, 'not-installed');
  assert.equal(result.repositoryAvailability, 'not-queried');
});

test('RPM database failure cannot be misclassified as missing dependency', () => {
  assert.equal(probeFedora(req(), { command: () => fail('', 'error: rpmdb open failed') }).status, 'probe-error');
});

test('RPM provider information must actually expose the requested capability', () => {
  const command = (_name, args) => args.includes('--whatprovides')
    ? ok('fixture\t0\t2\t1.fc45\tnoarch\tfixture-2-1.fc45.src.rpm\n')
    : ok('mvn(test:different) = 1\n');
  assert.equal(probeFedora(req(), { command }).status, 'probe-error');
});

test('runtime-only evidence can never certify complete source-build readiness', () => {
  const report = makeReport({ target: 'debian', input: { sha256: digest }, environment: {}, findings: [{ ...req(), status: 'installed-candidate' }], collectedAt: '2026-09-23T00:00:00Z' });
  assert.equal(report.readiness, 'not-established');
  assert.equal(report.coverage.runtime, 'catalog');
  assert.equal(report.coverage.buildPlugins, 'not-assessed');
  assert.equal(report.coverage.annotationProcessors, 'not-assessed');
  assert.equal(report.coverage.tests, 'not-assessed');
  assert.equal(report.validation.sourceBuild, 'not-run');
});

test('Markdown shows limitations even when every candidate is installed', () => {
  const report = makeReport({ target: 'fedora', input: { sha256: digest }, environment: {}, findings: [{ ...req(), status: 'installed-candidate', compatibility: 'not-tested' }], collectedAt: '2026-09-23T00:00:00Z' });
  const text = renderReport(report);
  assert.match(text, /not-established/); assert.match(text, /not-assessed/); assert.match(text, /not-run/);
});

test('mismatched distribution cannot be labelled as the requested target', () => {
  assert.throws(() => validateEnvironment('fedora', 'ID=debian\nVERSION_CODENAME=bookworm\n'), /requires.*fedora/i);
  assert.throws(() => validateEnvironment('debian', 'ID=ubuntu\nID_LIKE=debian\n'), /requires.*debian/i);
  assert.equal(validateEnvironment('debian', 'ID=debian\n').id, 'debian');
});

test('subprocess failure and timeout are explicit', () => {
  assert.notEqual(runCommand('norm-command-that-does-not-exist', []).error, null);
  assert.notEqual(runCommand(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { timeout: 30 }).error, null);
});

test('CLI help and report rendering work in a real Node subprocess', t => {
  const dir = temporary(t);
  const script = fileURLToPath(new URL('./distribution-preflight.mjs', import.meta.url));
  const help = spawnSync(process.execPath, [script, '--help'], { encoding: 'utf8' });
  assert.equal(help.status, 0, help.stderr); assert.match(help.stdout, /collect/);
  const report = makeReport({ target: 'debian', input: { sha256: digest }, environment: {}, findings: [], collectedAt: '2026-09-23T00:00:00Z' });
  const input = join(dir, 'report.json'); const output = join(dir, 'report.md');
  writeFileSync(input, JSON.stringify(report));
  const rendered = spawnSync(process.execPath, [script, 'render', '--input', input, '--output', output], { encoding: 'utf8' });
  assert.equal(rendered.status, 0, rendered.stderr); assert.match(readFileSync(output, 'utf8'), /not-established/);
});

test('CLI does not overwrite existing evidence files', t => {
  const dir = temporary(t); const script = fileURLToPath(new URL('./distribution-preflight.mjs', import.meta.url));
  const input = join(dir, 'report.json'); const output = join(dir, 'report.md');
  writeFileSync(input, JSON.stringify(makeReport({ target: 'debian', input: { sha256: digest }, environment: {}, findings: [] })));
  writeFileSync(output, 'keep this evidence');
  const result = spawnSync(process.execPath, [script, 'render', '--input', input, '--output', output], { encoding: 'utf8' });
  assert.equal(result.status, 1); assert.equal(readFileSync(output, 'utf8'), 'keep this evidence');
});

test('catalog artifact coordinates require explicit string fields', () => {
  const input = catalog();
  input.artifacts[0].group = undefined;
  input.dependencies['undefined:root:1'] = input.dependencies['test:root:1'];
  delete input.dependencies['test:root:1'];
  input.roots = ['undefined:root:1']; input.purposes.execution = [...input.roots];
  input.artifacts[0].components[0] = 'undefined:root:1';
  assert.throws(() => requirements(input), /coordinate/i);
});

test('unsupported purpose domains are not silently discarded', () => {
  const input = catalog(); input.purposes.newDomain = [];
  assert.throws(() => requirements(input), /purpose/i);
});

test('a probe cannot combine a safe coordinate with an unrelated filesystem path', t => {
  const dir = temporary(t); candidate(dir);
  assert.throws(() => probeDebian({ ...req(), group: '..' }, { repository: join(dir, 'repo'), command: debianOwner }), /coordinate/i);
});

test('an unowned Maven symlink is not accepted merely because its target is owned', t => {
  const dir = temporary(t); const base = candidate(dir);
  const jar = join(base, 'lib-1.jar'); rmSync(jar);
  const target = join(dir, 'system.jar'); writeFileSync(target, 'system library'); symlinkSync(target, jar);
  const command = (name, args) => args[0] === '-S' && args.at(-1) === jar
    ? fail('', 'dpkg-query: no path found matching pattern')
    : debianOwner(name, args);
  assert.equal(probeDebian(req(), { repository: join(dir, 'repo'), command }).status, 'unverified-candidate');
});

test('a merged component inherits the purpose of its physical owner without a graph edge', () => {
  const input = catalog();
  input.dependencies['test:root:1'] = ['test:child:2'];
  const result = requirements(input).find(value => value.coordinate === 'test:merged:1');
  assert.deepEqual(result.purposes, ['execution']);
});

const parallelFedoraPackages = ({ defaultInstalled = false, pom = false } = {}) => {
  const base = pom ? 'mvn(test:lib:pom:)' : 'mvn(test:lib)';
  const parallel = pom ? 'mvn(test:lib:pom:2)' : 'mvn(test:lib:2)';
  return (_program, args) => {
    if (args[0] === '-qa') return ok(`${defaultInstalled ? `${base} = 1\n` : ''}${parallel} = 2\nmvn(test:lib:jar:sources:2) = 2\nmvn(test:library:2) = 2\n`);
    if (args.includes('--whatprovides')) {
      const requested = args[args.indexOf('--whatprovides') + 1];
      if (requested === base && !defaultInstalled) return fail(`no package provides ${base}\n`);
      if (requested === base) return ok('fixture\t0\t1\t1.fc45\tnoarch\tfixture-1-1.fc45.src.rpm\n');
      if (requested === parallel) return ok('fixture-compat\t0\t2\t1.fc45\tnoarch\tfixture-2-1.fc45.src.rpm\n');
      throw new Error(`Unexpected capability query: ${requested}`);
    }
    return ok(`${base} = 1\n${parallel} = 2\n`);
  };
};

test('Fedora versioned capabilities are mapping candidates, not missing packages or compatible defaults', () => {
  const result = probeFedora(req(), { command: parallelFedoraPackages() });
  assert.equal(result.status, 'mapping-required');
  assert.equal(result.candidates.length, 0);
  assert.equal(result.alternatives[0].capability, 'mvn(test:lib:2)');
  assert.equal(result.alternatives[0].mapping, 'not-configured');
  assert.equal(result.alternatives[0].candidates[0].package, 'fixture-compat');
  assert.equal(result.compatibility, 'not-tested');
});

test('Fedora parallel packages remain visible when a default artifact is also installed', () => {
  const result = probeFedora(req(), { command: parallelFedoraPackages({ defaultInstalled: true }) });
  assert.equal(result.status, 'installed-candidate');
  assert.equal(result.candidates[0].package, 'fixture');
  assert.equal(result.alternatives[0].candidates[0].package, 'fixture-compat');
});

test('Fedora metadata lookup distinguishes parallel POM capabilities from JAR capabilities', () => {
  const result = probeFedora(req('metadata'), { command: parallelFedoraPackages({ pom: true }) });
  assert.equal(result.status, 'mapping-required');
  assert.deepEqual(result.alternatives.map(entry => entry.capability), ['mvn(test:lib:pom:2)']);
});

test('Fedora compatibility lookup excludes classifiers and similarly named artifacts', () => {
  const result = probeFedora(req(), { command: parallelFedoraPackages() });
  assert.deepEqual(result.alternatives.map(entry => entry.capability), ['mvn(test:lib:2)']);
});

test('failure to enumerate installed RPM capabilities is a probe error', () => {
  const command = (_program, args) => args.includes('--whatprovides')
    ? fail('no package provides mvn(test:lib)\n')
    : fail('', 'error: rpmdb open failed');
  assert.equal(probeFedora(req(), { command }).status, 'probe-error');
});

test('Markdown exposes required Fedora coordinate mapping without asserting compatibility', () => {
  const finding = { ...req(), status: 'mapping-required', candidates: [], compatibility: 'not-tested', alternatives: [{ capability: 'mvn(test:lib:2)', mapping: 'not-configured', candidates: [{ package: 'fixture-compat', version: '2', release: '1.fc45' }] }] };
  const report = makeReport({ target: 'fedora', input: { sha256: digest }, environment: {}, findings: [finding] });
  const text = renderReport(report);
  assert.match(text, /mvn\(test:lib:2\)/);
  assert.match(text, /fixture-compat/);
  assert.match(text, /not-configured/);
  assert.match(text, /not-tested/);
});
