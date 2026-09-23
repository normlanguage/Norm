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
  assert.equal(jobs.accept.steps.find(step => step.name === 'Download final extension').with.name, 'extension-universal');
  assert.match(jobs.accept.steps.find(step => step.name === 'Extract final extension').run, /extract-vsix/);
  assert.match(jobs.accept.env.NORM_TEST_EXTENSION, /extracted-extension$/);
  assert.match(jobs.accept.steps.find(step => step.name === 'Verify packaged language server').env.NORM_CLI, /extracted-extension\/bin\//);
  assert.equal(jobs.build.steps.find(step => step.name === 'Upload self-contained CLI for the universal extension').with.path, 'release-stage');
  assert.ok(!jobs.accept.steps.some(step => step.name === 'Download packaged runtime'));
  assert.ok(!jobs.build.steps.some(step => /verify-cli|spotless:check|smoke:lsp|\sverify\b/.test(step.run ?? '')));
});

test('daily toolchain does not build ecosystem applications or native samples', () => {
  const daily = workflow('toolchain');
  assert.ok(!JSON.stringify(daily).includes('verify-cli.mjs'));
  assert.deepEqual(Object.keys(workflow('native-size').on), ['workflow_dispatch']);
});

test('release validation shares the publishing DAG without branch write access', () => {
  const release = workflow('release');
  assert.deepEqual(release.on.push.branches, ['codex/macos-maven-validation']);
  assert.deepEqual(release.on.pull_request.branches, ['main']);
  assert.equal(release.permissions.contents, 'read');
  assert.match(release.jobs.publish.if, /github\.event_name == 'push'.*refs\/tags\/v/);
  assert.equal(release.jobs.publish.permissions.contents, 'write');
  assert.ok(release.jobs.build.steps.some(step => step.run?.includes('-Prelease clean package')));
  assert.match(release.jobs.build.steps.find(step => step.name === 'Verify target architecture')?.run ?? '', /process\.platform.*process\.arch.*matrix\.target/);
  assert.ok(release.jobs.verify.steps.some(step => step.run?.includes(' verify')));
  assert.equal(release.jobs.verify.steps.find(step => step.name === 'Verify native build evidence tools').env.NORM_VERSION, '${{ needs.prepare.outputs.version }}');
  assert.ok(!JSON.stringify(release.jobs.build).includes('gradlew'));
  assert.ok(!JSON.stringify(release.jobs.verify).includes('gradlew'));
});

test('documentation branch validation cannot deploy the site', () => {
  const docs = workflow('deploy-docs');
  assert.equal(docs.permissions.contents, 'read');
  assert.equal(docs.jobs.deploy.if, "github.ref == 'refs/heads/main'");
  assert.equal(docs.jobs.deploy.permissions.pages, 'write');
  assert.ok(docs.jobs.build.steps.some(step => step.run?.includes('mvnw')));
});
