import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { appendFileSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { baselineAction } from './native-baseline.mjs';
import { BaselineStore, prepareBaseline } from './native-baseline-store.mjs';
import { compareNativeSizeSets } from './compare-native-size.mjs';

function api(path) {
  const result = spawnSync('gh', ['api', path], { encoding: 'utf8', windowsHide: true, timeout: 30000 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, result.stderr);
  return JSON.parse(result.stdout);
}

const publish = process.argv[2] === '--publish';
const root = resolve(process.argv[publish ? 3 : 2] ?? 'build/reports/native-size');
const proposal = resolve('build/reports/native-baseline-proposal');
let store;
let stage = 'context-invalid';
let result;
try {
  const repository = process.env.GITHUB_REPOSITORY;
  const runId = Number(process.env.GITHUB_RUN_ID);
  assert.match(repository ?? '', /^[\w.-]+\/[\w.-]+$/);
  assert.ok(Number.isSafeInteger(runId) && runId > 0, 'Invalid workflow run');
  const run = api(`repos/${repository}/actions/runs/${runId}`);
  const repo = api(`repos/${repository}`);
  assert.equal(run.repository.full_name, repository);
  const context = { repository, branch: repo.default_branch, run,
    resetReason: process.env.NORM_NATIVE_BASELINE_RESET_REASON?.trim() ?? '' };
  const identity = { repository, workflowId: run.workflow_id,
    platform: `${process.env.RUNNER_OS}-${process.env.RUNNER_ARCH}` };
  assert.ok(process.env.RUNNER_OS && process.env.RUNNER_ARCH, 'Missing platform');
  const provenance = { ...identity, runId, sourceSha: run.head_sha, branch: run.head_branch };
  stage = 'candidate-invalid';
  if (!publish) compareNativeSizeSets(root, root);
  stage = 'baseline-read-failed';
  store = new BaselineStore(`https://github.com/${repository}.git`, identity);
  const baseline = store.load();
  stage = 'baseline-policy-rejected';
  const action = baselineAction(context, baseline !== undefined);
  if (publish) {
    assert.ok(action !== 'compare', 'Publication requires initialization or explicit reset');
    const manifest = JSON.parse(readFileSync(resolve(root, 'manifest.json'), 'utf8'));
    for (const [key, value] of Object.entries(provenance)) assert.equal(manifest[key], value, `Proposal ${key} mismatch`);
    assert.equal(manifest.reason, context.resetReason || 'First verified default-branch baseline');
    stage = 'baseline-publication-failed';
    const commit = store.publish(root);
    result = { status: action === 'reset' ? 'reset' : 'initialized', ...provenance, baselineCommit: commit, ref: store.ref };
  } else if (action !== 'compare') {
    stage = 'baseline-proposal-invalid';
    prepareBaseline(root, proposal, { ...provenance, parent: baseline?.commit ?? null,
      reason: context.resetReason || 'First verified default-branch baseline' });
    result = { status: `${action}-pending`, ...provenance, ref: store.ref };
    if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, 'publish_baseline=true\n');
  } else {
    stage = 'incomparable-inputs';
    const comparison = compareNativeSizeSets(baseline.reports, root);
    result = { status: comparison.passed ? 'passed' : 'size-growth', ...provenance,
      baselineCommit: baseline.commit, ref: store.ref, ...comparison };
    if (!comparison.passed) process.exitCode = 1;
  }
} catch (error) {
  result = { status: stage, message: error.message };
  process.exitCode = 1;
} finally {
  store?.close();
}
if (!publish) writeFileSync(resolve(root, 'gate.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
if (process.env.GITHUB_STEP_SUMMARY) {
  const details = result.comparisons?.map(item => `${item.candidate}: delivery ${item.deltas.deliveryBytes >= 0 ? '+' : ''}${item.deltas.deliveryBytes} bytes`).join('\n');
  appendFileSync(process.env.GITHUB_STEP_SUMMARY,
    `## Native size baseline\n\nStatus: **${result.status}**\n\n` +
    (result.baselineCommit ? `Baseline commit: \`${result.baselineCommit}\`\n\n` : '') +
    `<pre>${(result.message ?? details ?? 'No size comparison is claimed for initialization or reset.').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')}</pre>\n`);
}
