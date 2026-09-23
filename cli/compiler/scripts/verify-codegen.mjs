import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, relative, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { releaseVersion } from './release-model.mjs';

const repository = resolve(import.meta.dirname, '../../..');
const evidence = resolve(process.argv[2] ?? join(repository, 'build/reports/codegen'));
mkdirSync(evidence, { recursive: true });
const workspace = mkdtempSync(join(tmpdir(), 'norm-codegen-'));
const generated = join(workspace, 'cli/compiler/target/generated-sources/norm');
const records = [];
try {
  const tracked = spawnSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], { cwd: repository, encoding: 'utf8' });
  assert.equal(tracked.status, 0, tracked.stderr);
  for (const name of new Set(tracked.stdout.split('\0').filter(Boolean))) {
    const source = join(repository, name);
    if (!existsSync(source) || !statSync(source).isFile()) continue;
    const destination = join(workspace, name);
    mkdirSync(dirname(destination), { recursive: true });
    copyFileSync(source, destination);
  }
  const schema = join(workspace, 'cli/compiler/stdlib-abi.json');
  const original = readFileSync(schema);
  run('clean', ['clean', 'package']);
  const baseline = snapshot(join(generated, 'dev/w0fv1/norm/abi'));
  const golden = JSON.parse(readFileSync(join(workspace, 'build-tools/src/test/resources/codegen/abi-golden.json'), 'utf8'));
  assert.deepEqual(Object.fromEntries(Object.entries(baseline).map(([name, hash]) => [`dev/w0fv1/norm/abi/${name}`, hash])), golden.outputs);
  run('repeat', ['package']);
  assert.deepEqual(snapshot(join(generated, 'dev/w0fv1/norm/abi')), baseline);
  const value = JSON.parse(original);
  value.version++;
  writeFileSync(schema, JSON.stringify(value, null, 2));
  run('schema-change', ['package']);
  assert.notEqual(snapshot(join(generated, 'dev/w0fv1/norm/abi'))['BuiltinAbi.java'], baseline['BuiltinAbi.java']);
  writeFileSync(schema, original);
  run('schema-restore', ['package']);
  assert.deepEqual(snapshot(join(generated, 'dev/w0fv1/norm/abi')), baseline);
  const source = join(workspace, 'build-tools/src/main/java/dev/w0fv1/norm/codegen/BuiltinAbiGenerator.java');
  const compiledGenerator = join(workspace, 'build-tools/target/classes/dev/w0fv1/norm/codegen/BuiltinAbiGenerator.class');
  const originalClass = digest(readFileSync(compiledGenerator));
  const code = readFileSync(source, 'utf8');
  assert.ok(code.includes('Expected ABI schema and output directory'));
  writeFileSync(source, code.replace('Expected ABI schema and output directory', 'Expected ABI schema and output directory arguments'));
  run('generator-change', ['package']);
  assert.notEqual(digest(readFileSync(compiledGenerator)), originalClass);
  assert.deepEqual(snapshot(join(generated, 'dev/w0fv1/norm/abi')), baseline);
  rmSync(join(generated, 'dev/w0fv1/norm/abi'), { recursive: true });
  run('output-rebuild', ['package']);
  assert.deepEqual(snapshot(join(generated, 'dev/w0fv1/norm/abi')), baseline);
  const jarTool = join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'jar.exe' : 'jar');
  const distribution = join(workspace, 'cli/compiler/target/norm-runtime/lib');
  const product = readdirSync(distribution).find(name => /^compiler-.*\.jar$/.test(name));
  assert.ok(product);
  const listing = spawnSync(jarTool, ['--list', '--file', join(distribution, product)], { encoding: 'utf8' });
  assert.equal(listing.status, 0, listing.stderr);
  assert.doesNotMatch(listing.stdout, /dev\/w0fv1\/norm\/codegen\//);
  assert.match(listing.stdout, /dev\/w0fv1\/norm\/abi\/BuiltinAbi.class/);
  assert.ok(readdirSync(distribution).every(name => !/build-tools|codegen|groovy|kotlin/i.test(name)));
  const catalog = JSON.parse(readFileSync(join(workspace, 'cli/compiler/target/generated-resources/toolchain/toolchain-artifacts.json')));
  assert.ok(catalog.artifacts.every(artifact => !/build-tools|codegen|groovy|kotlin/i.test(artifact.file)));
  writeFileSync(join(evidence, 'verification.json'), JSON.stringify({ passed: true, generatedFiles: Object.keys(baseline).length, generatorExcluded: true, records }, null, 2) + '\n');
  console.log(`ABI codegen lifecycle and product isolation verified: ${evidence}`);
} finally {
  rmSync(workspace, { recursive: true, force: true });
}

function run(name, tasks) {
  const wrapper = join(workspace, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw');
  const revision = process.env.NORM_VERSION ? [`-Drevision=${releaseVersion(process.env.NORM_VERSION)}`] : [];
  const result = spawnSync(process.platform === 'win32' ? process.env.ComSpec : wrapper,
    process.platform === 'win32' ? ['/d', '/c', 'call', wrapper, '-B', '-ntp', '-Dmaven.test.skip=true', ...revision, '-pl', 'cli/compiler', '-am', ...tasks] : ['-B', '-ntp', '-Dmaven.test.skip=true', ...revision, '-pl', 'cli/compiler', '-am', ...tasks],
    { cwd: workspace, encoding: 'utf8', timeout: 900000, windowsHide: true });
  const output = (result.stdout ?? '') + (result.stderr ?? '');
  writeFileSync(join(evidence, `${name}.log`), output);
  if (result.error) throw result.error;
  assert.equal(result.status, 0, output);
  records.push({ name, exitCode: result.status });
  console.log(`${name}: passed`);
  return output;
}

function snapshot(root) {
  const files = readdirSync(root, { recursive: true, withFileTypes: true }).filter(entry => entry.isFile());
  return Object.fromEntries(files.map(entry => {
    const file = join(entry.parentPath, entry.name);
    return [relative(root, file).replaceAll('\\', '/'), createHash('sha256').update(readFileSync(file)).digest('hex')];
  }).sort(([a], [b]) => a.localeCompare(b)));
}

function digest(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}
