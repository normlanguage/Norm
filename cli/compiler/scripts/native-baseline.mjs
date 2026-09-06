import assert from 'node:assert/strict';

export function baselineAction(context, exists) {
  const { run, repository, branch } = context;
  const trusted = run.repository?.full_name === repository
    && run.head_repository?.full_name === repository && run.head_branch === branch
    && ['push', 'workflow_dispatch'].includes(run.event);
  if (context.resetReason?.trim()) {
    assert.ok(trusted, 'Reset requires trusted default branch');
    assert.equal(run.event, 'workflow_dispatch', 'Reset requires manual dispatch');
    return exists ? 'reset' : 'initialize';
  }
  if (exists) return 'compare';
  assert.ok(trusted, 'Missing baseline: initialize through a verified default-branch run');
  return 'initialize';
}
