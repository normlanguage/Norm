import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, mkdirSync, writeFileSync, symlinkSync, readdirSync, readFileSync, rmSync, lstatSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { verifyNativeSize } from './verify-native-size.mjs';

test('archived diagnostics remain readable after the original build is removed', async () => {
  const root = mkdtempSync(resolve(tmpdir(), 'norm-size-archive-'));
  try {
    const source = resolve(root, 'source');
    const directory = resolve(source, '.norm/build-reports/app.exe/run-test');
    mkdirSync(directory, { recursive: true });
    const executable = resolve(source, 'app.exe');
    const content = Buffer.from('image');
    writeFileSync(executable, content);
    writeFileSync(resolve(directory, 'size.json'), JSON.stringify({
      schemaVersion: 1, sha256: createHash('sha256').update(content).digest('hex'),
      executableBytes: content.length, imageBytes: 5, codeBytes: 2, heapBytes: 3, reachableMethods: 1,
      deliveryBytes: content.length,
      runtimeFiles: [{ path: 'app.exe', bytes: content.length, sha256: createHash('sha256').update(content).digest('hex') }],
    }));
    writeFileSync(resolve(directory, 'build-output.json'), JSON.stringify({
      image_details: { total_bytes: 5, code_area: { bytes: 2 }, image_heap: { bytes: 3 } },
      analysis_results: { methods: { reachable: 1 } },
    }));
    writeFileSync(resolve(directory, 'dashboard.dump'), '{}');
    writeFileSync(resolve(directory, 'build.log'), 'built');
    writeFileSync(resolve(directory, 'native-image.args'), 'args');
    const analysis = resolve(directory, 'analysis');
    mkdirSync(analysis);
    writeFileSync(resolve(analysis, 'methods-dated.csv'), 'Id,Name\n1,main\n');
    symlinkSync(resolve(analysis, 'methods-dated.csv'), resolve(analysis, 'methods.csv'), 'file');
    const evidence = resolve(root, 'evidence');
    await assert.rejects(verifyNativeSize(executable, evidence));
    const artifact = { identity: 'maven:sample:library:1', path: 'unavailable-cache/library.jar', sha256: 'a'.repeat(64), bytes: 10 };
    for (const invalid of [
      { schemaVersion: 2, artifacts: [artifact] },
      { schemaVersion: 1, artifacts: [artifact, artifact] },
      { schemaVersion: 1, artifacts: [{ ...artifact, sha256: 'invalid' }] },
      { schemaVersion: 1, artifacts: [{ ...artifact, bytes: -1 }] },
      { schemaVersion: 1, artifacts: [{ ...artifact, identity: '' }] },
      { schemaVersion: 1, artifacts: [{ ...artifact, path: null }] },
    ]) {
      writeFileSync(resolve(directory, 'java-artifacts.json'), JSON.stringify(invalid));
      await assert.rejects(verifyNativeSize(executable, evidence));
    }
    writeFileSync(resolve(directory, 'java-artifacts.json'), JSON.stringify({ schemaVersion: 1, artifacts: [artifact] }));
    await assert.rejects(verifyNativeSize(executable, evidence), /build-inputs/);
    const input = { path: '/historical/launcher', kind: 'file', files: [{ path: 'launcher', bytes: 1, sha256: 'b'.repeat(64) }] };
    writeFileSync(resolve(directory, 'build-inputs.json'), JSON.stringify({ schemaVersion: 1,
      arguments: ['args'], classpath: [], archive: input, launcher: input }));
    const verified = await verifyNativeSize(executable, evidence);
    assert.equal(verified.size.sha256, createHash('sha256').update(content).digest('hex'));
    assert.equal(verified.directory, resolve(evidence, readdirSync(evidence)[0]));
    const archived = resolve(evidence, readdirSync(evidence)[0], 'analysis/methods.csv');
    assert.equal(lstatSync(archived).isSymbolicLink(), false);
    rmSync(source, { recursive: true });
    assert.equal(readFileSync(archived, 'utf8'), 'Id,Name\n1,main\n');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
