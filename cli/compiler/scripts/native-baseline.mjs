import assert from 'node:assert/strict';

export function validateBaselineInitialization(run, repository, branch) {
  assert.equal(run.event, 'workflow_dispatch', 'Baseline initialization requires manual dispatch');
  assert.equal(run.head_branch, branch, 'Baseline initialization requires default branch');
  assert.equal(run.repository?.full_name, repository, 'Baseline initialization repository mismatch');
  assert.equal(run.head_repository?.full_name, repository, 'Baseline initialization requires trusted repository');
}

export function trustedBaselineRuns(runs, context) {
  assert.ok(Number.isSafeInteger(context.runNumber) && context.runNumber > 0, 'Invalid current run number');
  assert.ok(Number.isSafeInteger(context.workflowId) && context.workflowId > 0, 'Invalid workflow identity');
  assert.ok(typeof context.repository === 'string' && /^[^/]+\/[^/]+$/.test(context.repository), 'Invalid repository');
  assert.ok(typeof context.branch === 'string' && context.branch.length > 0, 'Missing baseline branch');
  assert.ok(Array.isArray(runs), 'Missing workflow runs');
  const ids = new Set();
  for (const run of runs) {
    assert.ok(Number.isSafeInteger(run.id) && run.id > 0, 'Invalid run identity');
    assert.ok(!ids.has(run.id), 'Duplicate workflow run');
    ids.add(run.id);
    assert.ok(Number.isSafeInteger(run.run_number) && run.run_number > 0, 'Invalid run number');
  }
  return runs.filter(run => run.workflow_id === context.workflowId
    && run.repository?.full_name === context.repository
    && run.head_repository?.full_name === context.repository
    && run.head_branch === context.branch && ['push', 'workflow_dispatch'].includes(run.event)
    && run.status === 'completed' && run.conclusion === 'success'
    && run.run_number < context.runNumber)
    .sort((a, b) => b.run_number - a.run_number);
}

export function selectBaselineArtifact(artifacts, name, runId) {
  assert.ok(Array.isArray(artifacts), 'Missing artifact list');
  assert.ok(typeof name === 'string' && name.length > 0, 'Missing artifact name');
  assert.ok(Number.isSafeInteger(runId) && runId > 0, 'Invalid artifact run identity');
  const matches = artifacts.filter(artifact => artifact.name === name);
  assert.ok(matches.length <= 1, 'Ambiguous baseline artifact');
  if (matches.length === 0) return undefined;
  const artifact = matches[0];
  assert.ok(Number.isSafeInteger(artifact.id) && artifact.id > 0, 'Invalid artifact identity');
  assert.equal(artifact.workflow_run?.id, runId, 'Artifact run mismatch');
  assert.equal(artifact.expired, false, 'Baseline artifact expired');
  return artifact;
}
