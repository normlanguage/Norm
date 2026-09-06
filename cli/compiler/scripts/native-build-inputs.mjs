import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { isDeepStrictEqual } from 'node:util';

export function readBuildInputs(directory) {
  const snapshot = JSON.parse(readFileSync(resolve(directory, 'build-inputs.json'), 'utf8'));
  assert.equal(snapshot.schemaVersion, 1, 'Unsupported build input snapshot');
  assert.ok(Array.isArray(snapshot.arguments) && snapshot.arguments.length > 0, 'Build arguments are absent');
  assert.ok(snapshot.arguments.every(value => typeof value === 'string' && !value.includes('\0')), 'Invalid build argument');
  assert.ok(Array.isArray(snapshot.classpath), 'Build classpath is absent');
  for (const input of [...snapshot.classpath, snapshot.archive, snapshot.launcher]) {
    assert.ok(input && typeof input.path === 'string' && input.path.length > 0, 'Build input path is absent');
    assert.ok(input.kind === 'file' || input.kind === 'directory', 'Invalid build input kind');
    assert.ok(Array.isArray(input.files), 'Build input files are absent');
    if (input.kind === 'file') assert.equal(input.files.length, 1, 'File input requires one fingerprint');
    const paths = new Set();
    for (const file of input.files) {
      assert.ok(typeof file.path === 'string' && file.path.length > 0, 'Invalid input file path');
      assert.ok(!/[\\\0]/.test(file.path) && !/^[A-Za-z]:/.test(file.path) && file.path.split('/').every(part => part && part !== '.' && part !== '..'), 'Input file path is not relative');
      assert.ok(!paths.has(file.path), 'Duplicate input file path');
      paths.add(file.path);
      assert.ok(Number.isSafeInteger(file.bytes) && file.bytes >= 0, 'Invalid input file size');
      assert.ok(typeof file.sha256 === 'string' && /^[a-f0-9]{64}$/.test(file.sha256), 'Invalid input file SHA-256');
    }
  }
  assert.equal(snapshot.archive.kind, 'file', 'Application archive must be a file');
  assert.equal(snapshot.launcher.kind, 'file', 'Native launcher must be a file');
  return snapshot;
}

export function compareBuildInputs(baseline, candidate) {
  const content = ({ kind, files }) => ({ kind, files });
  const changes = {
    arguments: differences(baseline.arguments, candidate.arguments),
    classpath: differences(baseline.classpath.map(content), candidate.classpath.map(content)),
    archive: differences([content(baseline.archive)], [content(candidate.archive)]),
    launcher: differences([content(baseline.launcher)], [content(candidate.launcher)])
  };
  return { identicalBuildInputs: Object.values(changes).every(entries => entries.length === 0), buildInputChanges: changes };
}

function differences(baseline, candidate) {
  const result = [];
  for (let index = 0; index < Math.max(baseline.length, candidate.length); index++) {
    const before = baseline[index] ?? null, after = candidate[index] ?? null;
    if (!isDeepStrictEqual(before, after)) result.push({ index, before, after });
  }
  return result;
}
