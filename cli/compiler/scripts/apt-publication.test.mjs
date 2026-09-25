import assert from 'node:assert/strict';
import test from 'node:test';
import { planAptPublication } from './apt-publication.mjs';

const selected = [
  { version: '0.24.0', sourceCommit: 'a'.repeat(40), assetSha256: 'b'.repeat(64) },
  { version: '0.23.2', sourceCommit: 'c'.repeat(40), assetSha256: 'd'.repeat(64) },
];
const config = release => Buffer.from(JSON.stringify({ packageVersion: '1', packageRelease: release }));
const key = Buffer.from('public-key');
const published = content => ({ packages: selected.map(value => ({ ...value, sha256: 'e'.repeat(64) })), releasePackage: { identity: '1-1', version: '1', release: '1', releaseContentSha256: content } });

test('existing application-only publication adds keyring without rebuilding either application', () => {
  const prior = { packages: selected.map(value => ({ ...value, sha256: 'e'.repeat(64) })) };
  const plan = planAptPublication(selected, prior, config('1'), key);
  assert.equal(plan.changed, true);
  assert.equal(plan.releaseChanged, true);
  assert.equal(plan.releaseIdentity, '1-1');
  assert.deepEqual(plan.build, []);
  assert.deepEqual(plan.reuse, ['0.24.0', '0.23.2']);
});

test('keyring configuration and application assets unchanged skip publication', () => {
  const initial = planAptPublication(selected, null, config('1'), key);
  assert.deepEqual(planAptPublication(selected, published(initial.releaseContentSha256), config('1'), key), { changed: false, build: [], reuse: ['0.24.0', '0.23.2'], releaseIdentity: '1-1', releaseChanged: false, releaseUpgrade: false, releaseContentSha256: initial.releaseContentSha256 });
});

test('same-version key bytes cannot change, independent keyring upgrade reuses applications', () => {
  const initial = planAptPublication(selected, null, config('1'), key);
  const prior = published(initial.releaseContentSha256);
  assert.throws(() => planAptPublication(selected, prior, config('1'), Buffer.from('renewed-key')), /version increment/i);
  const updated = planAptPublication(selected, prior, config('2'), Buffer.from('renewed-key'));
  assert.equal(updated.changed, true);
  assert.equal(updated.releaseIdentity, '1-2');
  assert.deepEqual(updated.build, []);
  assert.deepEqual(updated.reuse, ['0.24.0', '0.23.2']);
  assert.throws(() => planAptPublication(selected, { ...prior, releasePackage: { ...prior.releasePackage, identity: '1-2', release: '2' } }, config('1'), key), /downgrade/i);
});
