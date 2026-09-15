import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';

const repository = resolve(import.meta.dirname, '../../..');
const archive = resolve(process.argv[2] ?? join(repository, 'cli/compiler/build/generated/resources/reachability-metadata/graalvm-reachability-metadata.zip'));
const evidence = resolve(process.argv[3] ?? join(repository, 'build/reports/reachability-metadata'));
const workspace = mkdtempSync(join(tmpdir(), 'norm-reachability-'));
const output = join(workspace, 'cli/compiler/build/generated/resources/reachability-metadata/graalvm-reachability-metadata.zip');
const source = join(workspace, 'metadata.zip');
const records = [];
mkdirSync(evidence, { recursive: true });
try {
  const original = readFileSync(archive);
  for (const name of ['settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties', 'cli/compiler/build.gradle.kts']) {
    const destination = join(workspace, name);
    mkdirSync(dirname(destination), { recursive: true });
    copyFileSync(join(repository, name), destination);
  }
  writeFileSync(source, original);
  run('local-offline', true, source);
  assert.deepEqual(readFileSync(output), original);
  const unchanged = run('unchanged-input', true, source);
  assert.match(unchanged, /:compiler:prepareReachabilityMetadata UP-TO-DATE/);
  assert.match(unchanged, /Reusing configuration cache/);
  writeFileSync(source, 'invalid metadata');
  assert.match(run('corrupt-input', false, source), /checksum mismatch/);
  assert.deepEqual(readFileSync(output), original);
  writeFileSync(source, original);
  run('restored-input', true, source);
  rmSync(output);
  run('missing-output', true, source);
  assert.deepEqual(readFileSync(output), original);
  rmSync(output);
  assert.match(run('offline-without-input', false), /Offline build requires -PnormReachabilityMetadata=/);
  assert.equal(existsSync(output), false);
  assert.match(run('missing-input', false, join(workspace, 'missing.zip')), /does not exist/);
  assert.equal(existsSync(output), false);
  writeFileSync(join(evidence, 'verification.json'), JSON.stringify({ passed: true, archiveSha256: createHash('sha256').update(original).digest('hex'), records }, null, 2) + '\n');
  console.log(`Reachability metadata verification passed: ${evidence}`);
} finally {
  assert.equal(dirname(workspace), tmpdir());
  rmSync(workspace, { recursive: true, force: true });
}

function run(name, success, input) {
  const args = [':compiler:prepareReachabilityMetadata', '--offline', '--no-build-cache', '--console=plain'];
  if (input) args.push(`-PnormReachabilityMetadata=${input}`);
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const result = spawnSync(java, ['-jar', join(workspace, 'gradle/wrapper/gradle-wrapper.jar'), ...args],
    { cwd: workspace, encoding: 'utf8', timeout: 180000, windowsHide: true });
  const text = (result.stdout ?? '') + (result.stderr ?? '');
  writeFileSync(join(evidence, `${name}.log`), text);
  if (result.error) throw result.error;
  assert.equal(result.status === 0, success, text);
  records.push({ name, exitCode: result.status });
  console.log(`${name}: passed`);
  return text;
}
