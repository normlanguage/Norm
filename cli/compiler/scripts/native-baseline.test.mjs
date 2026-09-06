import assert from 'node:assert/strict';
import { test } from 'node:test';
import { trustedBaselineRuns, selectBaselineArtifact, validateBaselineInitialization } from './native-baseline.mjs';

test('baseline initialization requires explicit trusted default-branch dispatch', () => {
  const manual = run(1, 20, { event: 'workflow_dispatch' });
  assert.doesNotThrow(() => validateBaselineInitialization(manual, context.repository, context.branch));
  for (const override of [{ event: 'push' }, { head_branch: 'feature' },
    { head_repository: { full_name: 'fork/Norm' } }, { repository: { full_name: 'fork/Norm' } }]) {
    assert.throws(() => validateBaselineInitialization({ ...manual, ...override }, context.repository, context.branch));
  }
});

const context = { repository: 'normlanguage/Norm', workflowId: 7, branch: 'main', runNumber: 20 };
const run = (id, run_number, overrides = {}) => ({ id, run_number, workflow_id: 7, head_branch: 'main',
  event: 'push', status: 'completed', conclusion: 'success',
  repository: { full_name: context.repository }, head_repository: { full_name: context.repository }, ...overrides });

test('baseline selection uses prior successful trusted branch runs in newest order', () => {
  const rows = [run(2, 12), run(1, 18), run(3, 20), run(4, 21),
    run(5, 19, { event: 'pull_request' }), run(6, 19, { head_branch: 'feature' }),
    run(7, 19, { workflow_id: 8 }), run(8, 19, { conclusion: 'failure' }),
    run(9, 19, { status: 'in_progress' }),
    run(10, 19, { head_repository: { full_name: 'fork/Norm' } }),
    run(11, 19, { repository: { full_name: 'another/Norm' } })];
  assert.deepEqual(trustedBaselineRuns(rows, context).map(item => item.id), [1, 2]);
  assert.deepEqual(trustedBaselineRuns([run(12, 19, { event: 'workflow_dispatch' })], context).map(item => item.id), [12]);
  assert.throws(() => trustedBaselineRuns([run(1, 18), run(1, 18)], context), /duplicate/i);
  assert.throws(() => trustedBaselineRuns(rows, { ...context, runNumber: 0 }), /run number/i);
});

test('artifact selection rejects ambiguous or expired evidence instead of choosing by size', () => {
  const artifact = { id: 1, name: 'native-size-Linux-X64', expired: false, workflow_run: { id: 18 } };
  assert.equal(selectBaselineArtifact([], artifact.name, 18), undefined);
  assert.deepEqual(selectBaselineArtifact([artifact], artifact.name, 18), artifact);
  assert.throws(() => selectBaselineArtifact([artifact, { ...artifact, id: 2 }], artifact.name, 18), /ambiguous/i);
  assert.throws(() => selectBaselineArtifact([{ ...artifact, expired: true }], artifact.name, 18), /expired/i);
  assert.throws(() => selectBaselineArtifact([{ ...artifact, workflow_run: { id: 19 } }], artifact.name, 18), /run mismatch/i);
});
