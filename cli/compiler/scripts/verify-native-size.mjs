import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { cp } from 'node:fs/promises';
import { basename, dirname, resolve } from 'node:path';
import { readJavaArtifacts } from './native-java-inputs.mjs';
import { verifyNativeMetrics } from './native-size-metrics.mjs';
import { readBuildInputs } from './native-build-inputs.mjs';

export async function verifyNativeSize(executable, evidenceRoot) {
  const root = resolve(dirname(executable), '.norm/build-reports', basename(executable));
  const builds = readdirSync(root, { withFileTypes: true }).filter(entry => entry.isDirectory());
  assert.equal(builds.length, 1, 'Native verification expects one build in a fresh directory');
  const directory = resolve(root, builds[0].name);
  const size = JSON.parse(readFileSync(resolve(directory, 'size.json'), 'utf8'));
  const raw = JSON.parse(readFileSync(resolve(directory, 'build-output.json'), 'utf8'));
  assert.equal(size.schemaVersion, 1);
  assert.equal(size.sha256, createHash('sha256').update(readFileSync(executable)).digest('hex'));
  assert.equal(size.executableBytes, statSync(executable).size);
  assert.ok(Array.isArray(size.runtimeFiles) && size.runtimeFiles.length > 0);
  let deliveryBytes = 0;
  const paths = new Set();
  for (const file of size.runtimeFiles) {
    const path = resolve(dirname(executable), file.path);
    assert.ok(path.startsWith(resolve(dirname(executable)) + (process.platform === 'win32' ? '\\' : '/')));
    assert.ok(!paths.has(path), `Duplicate runtime file: ${path}`);
    paths.add(path);
    assert.equal(file.bytes, statSync(path).size);
    assert.equal(file.sha256, createHash('sha256').update(readFileSync(path)).digest('hex'));
    deliveryBytes += file.bytes;
  }
  assert.ok(paths.has(resolve(executable)), 'Runtime delivery omits executable');
  assert.equal(size.deliveryBytes, deliveryBytes);
  verifyNativeMetrics(size, raw);
  JSON.parse(readFileSync(resolve(directory, 'dashboard.dump'), 'utf8'));
  assert.ok(statSync(resolve(directory, 'build.log')).size > 0);
  assert.ok(statSync(resolve(directory, 'native-image.args')).size > 0);
  readJavaArtifacts(directory);
  readBuildInputs(directory);
  mkdirSync(evidenceRoot, { recursive: true });
  const evidence = mkdtempSync(resolve(evidenceRoot, `${basename(executable)}-`));
  await cp(directory, evidence, { recursive: true, dereference: true });
  console.log(`Native size evidence: ${evidence}`);
  console.log(`Native size verified: ${JSON.stringify(size)}`);
  return { directory: evidence, size };
}
