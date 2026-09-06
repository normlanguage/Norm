import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { readBuildInputs, compareBuildInputs } from './native-build-inputs.mjs';

test('reads archived fingerprints without requiring historical paths and preserves order', t => {
  const root = mkdtempSync(join(tmpdir(), 'norm-build-inputs-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const file = { path: 'compiler.jar', bytes: 9, sha256: 'a'.repeat(64) };
  const input = { path: '/removed/compiler.jar', kind: 'file', files: [file] };
  const snapshot = { schemaVersion: 1, arguments: ['-O2', '-cp', 'original classpath'],
    classpath: [input, { path: '/removed/generated', kind: 'directory', files: [
      { ...file, path: 'z.class' }, { ...file, path: 'a.class' }
    ] }], archive: input, launcher: input };
  const save = value => writeFileSync(join(root, 'build-inputs.json'), JSON.stringify(value));
  assert.throws(() => readBuildInputs(root));
  save(snapshot);
  assert.deepEqual(readBuildInputs(root), snapshot);
  const relocated = structuredClone(snapshot);
  relocated.classpath[0].path = '/another/compiler.jar';
  assert.equal(compareBuildInputs(snapshot, relocated).identicalBuildInputs, true);
  relocated.classpath.reverse();
  assert.equal(compareBuildInputs(snapshot, relocated).buildInputChanges.classpath.length, 2);
  const options = structuredClone(snapshot);
  options.arguments[0] = '-Os';
  assert.equal(compareBuildInputs(snapshot, options).buildInputChanges.arguments.length, 1);
  for (const change of [
    value => { value.schemaVersion = 2; },
    value => { value.arguments = ['-O2', null]; },
    value => { value.classpath = null; },
    value => { delete value.archive; },
    value => { value.launcher.kind = 'unknown'; },
    value => { value.archive.files = []; },
    value => { value.archive.files[0].bytes = -1; },
    value => { value.archive.files[0].sha256 = 'bad'; },
    value => { value.classpath[1].files.push(value.classpath[1].files[0]); },
    value => { value.classpath[1].files[0].path = '../escape'; },
    value => { value.classpath[1].files[0].path = 'C:/escape'; },
    value => { value.classpath[1].files[0].path = 'a\\b'; },
  ]) {
    const invalid = structuredClone(snapshot);
    change(invalid);
    save(invalid);
    assert.throws(() => readBuildInputs(root));
  }
});
