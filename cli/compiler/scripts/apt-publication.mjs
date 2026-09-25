import { planManagedRelease } from './release-publication.mjs';

export function planAptPublication(selected, published, configBytes, publicKeyBytes, renew = false) {
  return planManagedRelease(selected, published, configBytes, publicKeyBytes, renew);
}
