import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { prepareTask, verifyTask } from './agent-benchmark.mjs';

test('preparation keeps acceptance outside the editable submission and refuses overwrites', () => {
  const directory = join(mkdtempSync(join(tmpdir(), 'norm-agent-benchmark-')), 'submission');
  const task = prepareTask('add_feature', directory);
  assert.equal(task.id, 'add_feature');
  assert.match(readFileSync(join(directory, 'app/api.norm'), 'utf8'), /itemTotal/);
  assert.throws(() => readFileSync(join(directory, 'app/tests/acceptance.norm')), /ENOENT/);
  assert.throws(() => prepareTask('add_feature', directory), /already exists/);
  assert.throws(() => prepareTask('unknown', `${directory}-unknown`), /unknown task/);
});

test('an invalid launcher is distinguished from a failed task submission', () => {
  const directory = mkdtempSync(join(tmpdir(), 'norm-agent-launcher-'));
  prepareTask('add_feature', join(directory, 'submission'));
  const result = verifyTask('add_feature', join(directory, 'submission'), [process.execPath, '-e', 'process.stdout.write("not-json")', '--'], join(directory, 'report.json'));
  assert.equal(result.outcome, 'invalid');
  assert.equal(result.passed, false);
});

test('compiler infrastructure failures do not count as a rejected submission', () => {
  const directory = mkdtempSync(join(tmpdir(), 'norm-agent-infrastructure-'));
  prepareTask('add_feature', join(directory, 'submission'));
  const program = 'console.log(JSON.stringify({schemaVersion:1,command:"test",exitCode:70,status:"internal_error"}));process.exitCode=70';
  const result = verifyTask('add_feature', join(directory, 'submission'), [process.execPath, '-e', program, '--'], join(directory, 'report.json'));
  assert.equal(result.outcome, 'invalid');
});
