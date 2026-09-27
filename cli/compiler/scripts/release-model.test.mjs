import assert from 'node:assert/strict';
import test from 'node:test';
import { releaseAssetName, releaseTargets } from './release-model.mjs';

test('release targets use the root Gradle build and matching runner architecture', () => {
  assert.equal(releaseTargets.length, 3);
  assert.equal(new Set(releaseTargets.map(target => target.target)).size, 3);
  for (const target of releaseTargets) {
    assert.equal(target.distribution, 'build/compiler/norm-runtime');
  }
  const windows = releaseTargets.find(target => target.target === 'win32-x64');
  assert.equal(windows.standalone, 'build/distributions/norm.exe');
  assert.equal(releaseAssetName('0.24.0', 'win32-x64'), 'norm.exe');
  assert.equal(releaseAssetName('0.24.0', 'linux-x64'), 'norm-v0.24.0-linux-x64.tar.gz');
  assert.equal(releaseAssetName('0.24.0', 'darwin-arm64'), 'norm-v0.24.0-macos-arm64.tar.gz');
  for (const target of releaseTargets) assert.equal(typeof target.asset, 'string');
  assert.equal(releaseTargets.find(target => target.target === 'darwin-arm64').runner, 'macos-15');
});
