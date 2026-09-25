import assert from 'node:assert/strict';
import test from 'node:test';
import { planRpmPublication } from './rpm-publication.mjs';

const selected = [
  { version: '0.24.0', sourceCommit: 'a'.repeat(40), assetSha256: 'b'.repeat(64) },
  { version: '0.23.2', sourceCommit: 'c'.repeat(40), assetSha256: 'd'.repeat(64) },
];
const config = release => Buffer.from(JSON.stringify({ packageVersion: '1', packageRelease: release }));
const key = Buffer.from('public-key');

test('unchanged application, configuration, and key skip publication', () => {
  const first = planRpmPublication(selected, null, config('1'), key);
  const published = { packages: selected.map(value => ({ ...value, sha256: 'e'.repeat(64) })), releasePackage: { identity: first.releaseIdentity, version: '1', release: '1', releaseContentSha256: first.releaseContentSha256 } };
  assert.deepEqual(planRpmPublication(selected, published, config('1'), key), { changed: false, build: [], reuse: ['0.24.0', '0.23.2'], releaseIdentity: '1-1', releaseChanged: false, releaseUpgrade: false, releaseContentSha256: first.releaseContentSha256 });
});

test('the same release NEVRA cannot silently replace its public key', () => {
  const first = planRpmPublication(selected, null, config('1'), key);
  const published = { packages: selected.map(value => ({ ...value, sha256: 'e'.repeat(64) })), releasePackage: { identity: '1-1', version: '1', release: '1', releaseContentSha256: first.releaseContentSha256 } };
  assert.throws(() => planRpmPublication(selected, published, config('1'), Buffer.from('different-key')), /version increment/i);
});

test('a release configuration update publishes while reusing both application RPMs', () => {
  const first = planRpmPublication(selected, null, config('1'), key);
  const published = { packages: selected.map(value => ({ ...value, sha256: 'e'.repeat(64) })), releasePackage: { identity: '1-1', version: '1', release: '1', releaseContentSha256: first.releaseContentSha256 } };
  assert.deepEqual(planRpmPublication(selected, published, config('2'), key), { changed: true, build: [], reuse: ['0.24.0', '0.23.2'], releaseIdentity: '1-2', releaseChanged: true, releaseUpgrade: true, releaseContentSha256: planRpmPublication(selected, null, config('2'), key).releaseContentSha256 });
  const advanced = { ...published, releasePackage: { ...published.releasePackage, identity: '1-2', release: '2' } };
  assert.throws(() => planRpmPublication(selected, advanced, config('1'), key), /downgrade/i);
});
