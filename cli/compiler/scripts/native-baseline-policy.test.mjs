import assert from 'node:assert/strict';
import { test } from 'node:test';
import { baselineAction } from './native-baseline.mjs';

const context = { repository: 'normlanguage/Norm', branch: 'main', run: {
  event: 'push', head_branch: 'main', repository: { full_name: 'normlanguage/Norm' },
  head_repository: { full_name: 'normlanguage/Norm' }
} };

test('only trusted default branch can initialize an absent baseline', () => {
  assert.equal(baselineAction(context, false), 'initialize');
  for (const override of [{ event: 'pull_request' }, { head_branch: 'feature' },
    { head_repository: { full_name: 'fork/Norm' } }]) {
    const untrusted = { ...context, run: { ...context.run, ...override } };
    assert.throws(() => baselineAction(untrusted, false), /missing baseline/i);
    assert.equal(baselineAction(untrusted, true), 'compare');
  }
});

test('existing baseline is compared unless a trusted manual reset has a reason', () => {
  assert.equal(baselineAction(context, true), 'compare');
  assert.throws(() => baselineAction({ ...context, resetReason: 'toolchain upgrade' }, true), /manual/i);
  const manual = { ...context, run: { ...context.run, event: 'workflow_dispatch' } };
  assert.equal(baselineAction({ ...manual, resetReason: 'toolchain upgrade' }, true), 'reset');
  assert.equal(baselineAction({ ...manual, resetReason: '  ' }, true), 'compare');
  assert.throws(() => baselineAction({ ...manual, run: { ...manual.run, head_branch: 'feature' }, resetReason: 'upgrade' }, true), /trusted/i);
});
