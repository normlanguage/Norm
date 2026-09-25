import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { releaseAssetName } from './release-model.mjs';

export function verifiedReleaseAsset(version, target, assetsDirectory) {
  const name = releaseAssetName(version, target);
  const lines = readFileSync(join(assetsDirectory, 'SHA256SUMS'), 'utf8').trim().split(/\r?\n/);
  const parsed = lines.map(line => /^([a-fA-F0-9]{64}) [ *](.+)$/.exec(line));
  if (parsed.some(match => !match)) throw new Error('Invalid release checksum line');
  const matches = parsed.filter(match => match[2] === name);
  if (matches.length !== 1) throw new Error(`Expected one release checksum for ${name}`);
  const path = join(assetsDirectory, name);
  const hash = createHash('sha256').update(readFileSync(path)).digest('hex');
  if (hash !== matches[0][1].toLowerCase()) throw new Error(`Release checksum mismatch: ${name}`);
  return { name, path, hash };
}
