import { readFileSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const releaseTargets = JSON.parse(readFileSync(new URL('../release-targets.json', import.meta.url), 'utf8'));
if (!Array.isArray(releaseTargets) || releaseTargets.length === 0 ||
    releaseTargets.some(value => ['runner', 'target', 'platform', 'distribution', 'launcher'].some(key => typeof value[key] !== 'string' || !value[key])) ||
    new Set(releaseTargets.map(value => value.target)).size !== releaseTargets.length) {
  throw new Error('Invalid release target manifest');
}

export function releaseVersion(value) {
  if (typeof value !== 'string' || !/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value)) {
    throw new Error(`Invalid release version: ${value}`);
  }
  return value;
}

export function readProjectVersion(repository) {
  const pom = readFileSync(resolve(repository, 'pom.xml'), 'utf8');
  const revisions = [...pom.matchAll(/<revision>\s*([^<>\s]+)\s*<\/revision>/g)];
  if (revisions.length !== 1 || !/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-SNAPSHOT)?$/.test(revisions[0][1])) {
    throw new Error('Root pom.xml must declare one semantic revision');
  }
  return revisions[0][1];
}

export function releaseAssetName(version, target) {
  releaseVersion(version);
  const definition = releaseTargets.find(value => value.target === target);
  if (!definition) throw new Error(`Unsupported release target: ${target}`);
  return definition.standalone ? basename(definition.standalone) : `norm-v${version}-${definition.platform}.tar.gz`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 4) throw new Error('Usage: release-model.mjs <version> <target>');
  console.log(releaseAssetName(process.argv[2], process.argv[3]));
}
