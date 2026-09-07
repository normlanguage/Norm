import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
import yaml from 'js-yaml';

const root = resolve(import.meta.dirname, '../../../..');
const workflow = name => yaml.load(readFileSync(resolve(root, '.github/workflows', name + '.yml'), 'utf8'));

test('release accepts completed packages before publication', () => {
  const { jobs } = workflow('release');
  assert.equal(jobs.build.needs, 'prepare');
  assert.deepEqual(jobs['package-extension'].needs, ['prepare', 'build']);
  for (const name of ['verify', 'accept']) assert.deepEqual(jobs[name].needs, ['prepare', 'package-extension']);
  assert.deepEqual(jobs.publish.needs, ['prepare', 'verify', 'accept']);
  assert.equal(jobs.accept.steps.filter(step => step.run?.includes('verify-cli.mjs')).length, 1);
  assert.match(jobs.accept.steps.find(step => step.name === 'Stage packaged editor runtime').run, /stageCliBundle/);
  assert.match(jobs.accept.steps.find(step => step.name === 'Verify packaged language server').env.NORM_CLI, /cli\/extensions\/vscode\/bin\//);
  assert.equal(jobs.build.steps.find(step => step.name === 'Upload self-contained CLI for the universal extension').with.path, 'release-stage');
  assert.equal(jobs.accept.steps.find(step => step.name === 'Download packaged runtime').with.path, 'binaries/runtime-${{ matrix.target }}');
  assert.ok(!jobs.build.steps.some(step => /verify-cli|qualityCheck|smoke:lsp/.test(step.run ?? '')));
});

test('daily toolchain does not build ecosystem applications or native samples', () => {
  const daily = workflow('toolchain');
  assert.ok(!JSON.stringify(daily).includes('verify-cli.mjs'));
  assert.deepEqual(Object.keys(workflow('native-size').on), ['workflow_dispatch']);
});
