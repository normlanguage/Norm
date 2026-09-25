import { createHash } from 'node:crypto';
import { planPublication } from './release-publication.mjs';

export function planRpmPublication(selected, published, configBytes, publicKeyBytes, renew = false) {
  const config = JSON.parse(configBytes.toString());
  if (!/^[1-9]\d*$/.test(config.packageVersion) || !/^[1-9]\d*$/.test(config.packageRelease)) throw new Error('Invalid RPM release package version');
  const releaseIdentity = `${config.packageVersion}-${config.packageRelease}`;
  const releaseContentSha256 = createHash('sha256').update(configBytes).update(publicKeyBytes).digest('hex');
  const prior = published?.releasePackage;
  if (prior?.identity === releaseIdentity && prior.releaseContentSha256 !== releaseContentSha256) throw new Error('RPM release configuration or key changed without a version increment');
  if (prior && (BigInt(config.packageVersion) < BigInt(prior.version) || (config.packageVersion === prior.version && BigInt(config.packageRelease) < BigInt(prior.release)))) throw new Error('RPM release configuration downgrade');
  const releaseChanged = !prior || prior.identity !== releaseIdentity;
  return { ...planPublication(selected, published, releaseChanged || renew), releaseIdentity, releaseChanged, releaseUpgrade: Boolean(published && releaseChanged), releaseContentSha256 };
}
