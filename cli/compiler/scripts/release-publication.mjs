import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { releaseAssetName, releaseTargets, releaseVersion } from './release-model.mjs';

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, ...options });
  if (result.error || result.status !== 0) throw new Error(`${command} ${args.join(' ')} failed: ${result.error?.message ?? result.stderr}`);
  return result.stdout;
}

function compareVersions(left, right) {
  const a = left.split('.').map(Number);
  const b = right.split('.').map(Number);
  for (let index = 0; index < 3; index++) {
    if (a[index] !== b[index]) return a[index] - b[index];
  }
  return 0;
}

export function selectCompleteReleases(releases) {
  const complete = releases.flatMap(release => {
    if (release.draft || release.prerelease || !release.tag_name?.startsWith('v')) return [];
    const version = release.tag_name.slice(1);
    try { releaseVersion(version); } catch { return []; }
    const assets = new Map(release.assets?.map(asset => [asset.name, asset]) ?? []);
    const required = [...releaseTargets.map(target => releaseAssetName(version, target.target)), `norm-language-support-v${version}.vsix`, 'SHA256SUMS'];
    if (assets.size !== release.assets?.length || release.assets.filter(asset => asset.name.endsWith('.vsix')).length !== 1) return [];
    if (required.some(name => !Number.isSafeInteger(assets.get(name)?.size) || assets.get(name).size <= 0 || !/^sha256:[a-fA-F0-9]{64}$/.test(assets.get(name)?.digest ?? ''))) return [];
    const linux = assets.get(releaseAssetName(version, 'linux-x64'));
    return [{ version, tag: release.tag_name, assetName: linux.name, assetSha256: linux.digest.slice(7).toLowerCase() }];
  }).sort((left, right) => compareVersions(right.version, left.version));
  if (complete.length < 2) throw new Error('At least two complete official releases are required');
  return complete.slice(0, 2);
}

export function planPublication(selected, published, renew = false) {
  if (!published) return { changed: true, build: selected.map(value => value.version), reuse: [] };
  if (published.packages.some(value => compareVersions(value.version, selected[0].version) > 0)) throw new Error('Refusing release downgrade');
  const existing = new Map(published.packages.map(value => [value.version, value]));
  const build = [];
  const reuse = [];
  for (const release of selected) {
    const prior = existing.get(release.version);
    if (!prior) build.push(release.version);
    else {
      if (prior.sourceCommit !== release.sourceCommit || prior.assetSha256 !== release.assetSha256) throw new Error(`Published same-version source changed: ${release.version}`);
      reuse.push(release.version);
    }
  }
  return { changed: build.length > 0 || renew, build, reuse };
}

export function planManagedRelease(selected, published, configBytes, publicKeyBytes, renew = false) {
  const config = JSON.parse(configBytes.toString());
  if (typeof config.packageVersion !== 'string' || typeof config.packageRelease !== 'string' || !/^[1-9]\d*$/.test(config.packageVersion) || !/^[1-9]\d*$/.test(config.packageRelease)) throw new Error('Invalid release package version');
  const releaseIdentity = `${config.packageVersion}-${config.packageRelease}`;
  const releaseContentSha256 = createHash('sha256').update(configBytes).update(publicKeyBytes).digest('hex');
  const prior = published?.releasePackage;
  if (prior?.identity === releaseIdentity && prior.releaseContentSha256 !== releaseContentSha256) throw new Error('Release configuration or key changed without a version increment');
  if (prior && (BigInt(config.packageVersion) < BigInt(prior.version) || (config.packageVersion === prior.version && BigInt(config.packageRelease) < BigInt(prior.release)))) throw new Error('Release configuration downgrade');
  const releaseChanged = !prior || prior.identity !== releaseIdentity;
  return { ...planPublication(selected, published, releaseChanged || renew), releaseIdentity, releaseChanged, releaseUpgrade: Boolean(published && releaseChanged), releaseContentSha256 };
}

export function assertSigningKeyLifetime(keyListing, now = Date.now()) {
  const signingSubkeys = keyListing.split('\n').filter(line => {
    const fields = line.split(':');
    return fields[0] === 'sub' && fields[11]?.toLowerCase().includes('s');
  });
  if (signingSubkeys.length !== 1) throw new Error('Expected one signing subkey');
  const expires = Number(signingSubkeys[0].split(':')[6]);
  if (!Number.isSafeInteger(expires) || expires * 1000 <= now + 30 * 24 * 60 * 60 * 1000) throw new Error('Signing subkey expires within 30 days; explicitly renew and review the public key');
}

export function officialReleaseSources(toolingDirectory) {
  toolingDirectory = resolve(toolingDirectory);
  const records = JSON.parse(run('gh', ['api', 'repos/normlanguage/Norm/releases?per_page=100']));
  const selected = selectCompleteReleases(records);
  for (const value of selected) {
    run('git', ['fetch', '--no-tags', 'origin', `refs/tags/${value.tag}`], { cwd: toolingDirectory });
    value.sourceCommit = run('git', ['rev-parse', 'FETCH_HEAD^{commit}'], { cwd: toolingDirectory }).trim();
  }
  return selected;
}

export function downloadAttestedReleaseAsset(release, directory) {
  directory = resolve(directory);
  mkdirSync(directory, { recursive: true });
  run('gh', ['release', 'download', release.tag, '--repo', 'normlanguage/Norm', '--pattern', release.assetName, '--pattern', 'SHA256SUMS', '--dir', directory]);
  const archive = join(directory, release.assetName);
  const digest = createHash('sha256').update(readFileSync(archive)).digest('hex');
  if (digest !== release.assetSha256) throw new Error(`GitHub release asset digest mismatch: ${release.version}`);
  const attestation = run('gh', ['attestation', 'verify', archive, '--repo', 'normlanguage/Norm', '--source-ref', `refs/tags/${release.tag}`, '--source-digest', release.sourceCommit, '--signer-workflow', 'normlanguage/Norm/.github/workflows/release.yml', '--format', 'json']);
  writeFileSync(join(directory, 'attestation.json'), attestation);
  return archive;
}
