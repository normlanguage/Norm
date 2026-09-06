import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export function readJavaArtifacts(directory) {
  const manifest = JSON.parse(readFileSync(resolve(directory, 'java-artifacts.json'), 'utf8'));
  assert.equal(manifest.schemaVersion, 1, 'Unsupported Java input manifest');
  assert.ok(Array.isArray(manifest.artifacts), 'Java input artifacts are absent');
  const identities = new Set();
  for (const artifact of manifest.artifacts) {
    assert.ok(typeof artifact.identity === 'string' && artifact.identity.trim().length > 0, 'Invalid Java artifact identity');
    assert.ok(!identities.has(artifact.identity), `Duplicate Java artifact identity: ${artifact.identity}`);
    identities.add(artifact.identity);
    assert.ok(typeof artifact.path === 'string' && artifact.path.trim().length > 0, 'Java artifact path is absent');
    assert.ok(typeof artifact.sha256 === 'string' && /^[a-f0-9]{64}$/.test(artifact.sha256), 'Invalid Java artifact SHA-256');
    assert.ok(Number.isSafeInteger(artifact.bytes) && artifact.bytes >= 0, 'Invalid Java artifact size');
  }
  return manifest.artifacts.map(({ identity, sha256, bytes }) => ({ identity, sha256, bytes }))
    .sort((left, right) => left.identity.localeCompare(right.identity));
}
