import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { compareNativeSize, compareNativeSizeSets } from './compare-native-size.mjs';
import { report } from './fixtures/native-size-report.mjs';

test('reports compiler input changes without treating them as identical builds', t => {
  const root = mkdtempSync(join(tmpdir(), 'norm-size-input-change-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const baseline = join(root, 'baseline'), candidate = join(root, 'candidate');
  report(baseline);
  const current = report(candidate);
  current.documents['build-inputs.json'].classpath[0].files[0].sha256 = 'c'.repeat(64);
  current.save();
  const result = compareNativeSize(baseline, candidate);
  assert.equal(result.passed, true);
  assert.equal(result.identicalBuildInputs, false);
  assert.equal(result.buildInputChanges.classpath.length, 1);
  assert.equal(result.buildInputChanges.classpath[0].index, 0);
});

test('matches complete report sets by verified inputs rather than directory names', t => {
  const root = mkdtempSync(join(tmpdir(), 'norm-size-sets-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const baseline = join(root, 'baseline'), candidate = join(root, 'candidate');
  mkdirSync(baseline); mkdirSync(candidate);
  report(join(baseline, 'old-random'));
  report(join(candidate, 'new-random'), 5);
  assert.equal(compareNativeSizeSets(baseline, candidate).passed, false);
  const result = compareNativeSizeSets(baseline, candidate, 5);
  assert.equal(result.passed, true);
  assert.equal(result.comparisons.length, 1);
  assert.equal(result.comparisons[0].deltas.deliveryBytes, 5);
  assert.throws(() => compareNativeSizeSets(baseline, candidate, -1), /budget/i);
  report(join(baseline, 'ambiguous'));
  assert.throws(() => compareNativeSizeSets(baseline, candidate), /ambiguous/i);
});

test('rejects missing samples, duplicate candidates and corrupt report sets', t => {
  const root = mkdtempSync(join(tmpdir(), 'norm-size-incomplete-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const baseline = join(root, 'baseline'), candidate = join(root, 'candidate');
  mkdirSync(baseline); mkdirSync(candidate);
  assert.throws(() => compareNativeSizeSets(baseline, candidate), /empty/i);
  report(join(baseline, 'sample'));
  const current = report(join(candidate, 'sample'));
  const missing = report(join(baseline, 'missing'));
  missing.documents['execution-verification.json'].sourceSha256 = 'c'.repeat(64);
  missing.save();
  assert.throws(() => compareNativeSizeSets(baseline, candidate), /coverage/i);
  rmSync(join(baseline, 'missing'), { recursive: true });
  current.documents['execution-verification.json'].sourceSha256 = 'd'.repeat(64);
  current.save();
  assert.throws(() => compareNativeSizeSets(baseline, candidate), /matching baseline/i);
  current.documents['execution-verification.json'].sourceSha256 = 'b'.repeat(64);
  current.save();
  report(join(candidate, 'duplicate'));
  assert.throws(() => compareNativeSizeSets(baseline, candidate), /duplicate candidate/i);
  rmSync(join(candidate, 'duplicate'), { recursive: true });
  mkdirSync(join(candidate, 'unfinished'));
  assert.throws(() => compareNativeSizeSets(baseline, candidate));
});


test('compares complete delivery against an explicit byte budget', t => {
  const root = mkdtempSync(join(tmpdir(), 'norm-size-comparison-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const baseline = join(root, 'baseline'), candidate = join(root, 'candidate');
  report(baseline);
  report(candidate, 5);
  assert.equal(compareNativeSize(baseline, candidate).passed, false);
  const result = compareNativeSize(baseline, candidate, 5);
  assert.equal(result.passed, true);
  assert.equal(result.deltas.deliveryBytes, 5);
  assert.equal(result.deltas.executableBytes, 5);
  assert.equal(result.deltas.codeBytes, 0);
  assert.throws(() => compareNativeSize(baseline, candidate, -1), /budget/i);
});

test('rejects incomparable inputs and incomplete evidence', t => {
  const root = mkdtempSync(join(tmpdir(), 'norm-size-comparison-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const baseline = join(root, 'baseline');
  report(baseline);
  const changes = [
    d => { d['build-inputs.json'].schemaVersion = 2; },
    d => { d['build-inputs.json'].classpath[0].files[0].bytes = -1; },
    d => { d['build-output.json'].general_info.graal_compiler.optimization_level = 's'; },
    d => { delete d['build-output.json'].general_info.c_compiler; },
    d => { d['execution-verification.json'].sourceSha256 = 'c'.repeat(64); },
    d => { d['execution-verification.json'].executableSha256 = 'c'.repeat(64); },
    d => { d['execution-verification.json'].runs.pop(); },
    d => { d['execution-verification.json'].runs[1].iteration = 1; },
    d => { d['execution-verification.json'].runs[2].output = 'wrong'; },
    d => { d['java-artifacts.json'].artifacts[0].sha256 = 'c'.repeat(64); },
    d => { d['java-artifacts.json'].artifacts.push(d['java-artifacts.json'].artifacts[0]); },
    d => { d['size.json'].deliveryBytes++; },
    d => { d['size.json'].heapBytes = -1; },
    d => { d['size.json'].codeBytes++; },
    d => { d['size.json'].imageBytes++; },
    d => { d['size.json'].reachableMethods++; },
    d => { d['build-output.json'].image_details.image_heap.bytes++; },
    d => { delete d['execution-verification.json'].runs; }
  ];
  changes.forEach((change, index) => {
    const candidate = join(root, String(index));
    const fixture = report(candidate);
    change(fixture.documents);
    fixture.save();
    assert.throws(() => compareNativeSize(baseline, candidate), `case ${index}`);
  });
});

test('accepts archived Java artifact paths', t => {
  const root = mkdtempSync(join(tmpdir(), 'norm-size-comparison-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const paths = ['baseline', 'candidate'].map(name => join(root, name));
  const fixtures = paths.map(path => report(path));
  fixtures[1].documents['java-artifacts.json'].artifacts[0].path = 'another-machine/library.jar';
  fixtures[1].save();
  assert.equal(compareNativeSize(...paths).passed, true);
});
