import { readFileSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const releaseTargets = JSON.parse(readFileSync(new URL('../release-targets.json', import.meta.url), 'utf8'));
if (!Array.isArray(releaseTargets) || releaseTargets.length === 0 ||
    releaseTargets.some(value => ['runner', 'target', 'platform', 'asset', 'distribution', 'launcher'].some(key => typeof value[key] !== 'string' || !value[key])) ||
    releaseTargets.some(value => value.asset.includes('/') || value.asset.includes('\\') || value.asset.replace('{version}', '').includes('{') || (value.standalone && basename(value.standalone) !== value.asset)) ||
    new Set(releaseTargets.map(value => value.asset)).size !== releaseTargets.length ||
    new Set(releaseTargets.map(value => value.target)).size !== releaseTargets.length) {
  throw new Error('Invalid release target manifest');
}

export function releaseVersion(value) {
  if (typeof value !== 'string' || !/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value)) {
    throw new Error(`Invalid release version: ${value}`);
  }
  return value;
}

export function releaseAssetName(version, target) {
  releaseVersion(version);
  const definition = releaseTargets.find(value => value.target === target);
  if (!definition) throw new Error(`Unsupported release target: ${target}`);
  return definition.asset.replace('{version}', version);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 4) throw new Error('Usage: release-model.mjs <version> <target>');
  console.log(releaseAssetName(process.argv[2], process.argv[3]));
}
