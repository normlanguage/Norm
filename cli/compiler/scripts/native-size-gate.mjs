import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { isDeepStrictEqual } from 'node:util';
import { trustedBaselineRuns, selectBaselineArtifact, validateBaselineInitialization } from './native-baseline.mjs';
import { readNativeSizeSet, compareNativeSizeSets } from './compare-native-size.mjs';

function gh(args) {
  const result = spawnSync('gh', args, { encoding: 'utf8', windowsHide: true, timeout: 30000 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, result.stderr);
  return result.stdout;
}

const repository = process.env.GITHUB_REPOSITORY;
const runId = Number(process.env.GITHUB_RUN_ID);
assert.ok(typeof repository === 'string' && /^[\w.-]+\/[\w.-]+$/.test(repository), 'Missing GitHub repository');
assert.ok(Number.isSafeInteger(runId) && runId > 0, 'Missing workflow run identity');
const root = resolve(process.argv[2] ?? 'build/reports/native-size');
const candidates = readNativeSizeSet(root);
for (let index = 0; index < candidates.length; index++) {
  assert.ok(!candidates.slice(0, index).some(previous => isDeepStrictEqual(previous.report.inputs, candidates[index].report.inputs)), 'Duplicate candidate scope');
}
const current = JSON.parse(gh(['api', `repos/${repository}/actions/runs/${runId}`]));
const repo = JSON.parse(gh(['api', `repos/${repository}`]));
assert.equal(current.repository.full_name, repository, 'Current run repository mismatch');
const artifactName = `native-size-${process.env.RUNNER_OS}-${process.env.RUNNER_ARCH}`;
assert.ok(process.env.RUNNER_OS && process.env.RUNNER_ARCH, 'Missing runner identity');
let gate;
if (process.env.NORM_NATIVE_BASELINE_INITIALIZE === 'true') {
  validateBaselineInitialization(current, repository, repo.default_branch);
  gate = { status: 'initialized', runId, artifactName, samples: candidates.length };
} else {
  const pages = JSON.parse(gh(['api', '--paginate', '--slurp',
    `repos/${repository}/actions/workflows/${current.workflow_id}/runs?branch=${encodeURIComponent(repo.default_branch)}&per_page=100`]));
  const runs = trustedBaselineRuns(pages.flatMap(page => page.workflow_runs), {
    repository, workflowId: current.workflow_id, branch: repo.default_branch, runNumber: current.run_number,
  });
  let baseline;
  for (const run of runs) {
    const artifactPages = JSON.parse(gh(['api', '--paginate', '--slurp', `repos/${repository}/actions/runs/${run.id}/artifacts?per_page=100`]));
    const artifact = selectBaselineArtifact(artifactPages.flatMap(page => page.artifacts), artifactName, run.id);
    if (artifact) { baseline = { run, artifact }; break; }
  }
  assert.ok(baseline, 'No trusted native baseline. Explicit default-branch initialization is required.');
  const directory = mkdtempSync(resolve(tmpdir(), 'norm-native-baseline-'));
  try {
    gh(['run', 'download', String(baseline.run.id), '--repo', repository, '--name', artifactName, '--dir', directory]);
    const comparison = compareNativeSizeSets(directory, root);
    gate = { status: comparison.passed ? 'passed' : 'failed', runId, baselineRunId: baseline.run.id,
      baselineArtifactId: baseline.artifact.id, ...comparison };
    if (!comparison.passed) process.exitCode = 1;
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}
writeFileSync(resolve(root, 'gate.json'), JSON.stringify(gate, null, 2) + '\n');
console.log(JSON.stringify(gate, null, 2));
