import assert from 'node:assert/strict';
import test from 'node:test';
import { releaseAssetName, releaseTargets } from './release-model.mjs';

test('release targets use Maven assets and matching runner architecture', () => {
  assert.equal(releaseTargets.length, 3);
  assert.equal(new Set(releaseTargets.map(target => target.target)).size, 3);
  for (const target of releaseTargets) {
    assert.equal(target.distribution, 'cli/compiler/target/norm-runtime');
  }
  const windows = releaseTargets.find(target => target.target === 'win32-x64');
  assert.equal(windows.standalone, 'cli/compiler/target/launcher/publish/norm.exe');
  assert.equal(releaseAssetName('0.24.0', 'win32-x64'), 'norm.exe');
  assert.equal(releaseTargets.find(target => target.target === 'darwin-arm64').runner, 'macos-15');
});
